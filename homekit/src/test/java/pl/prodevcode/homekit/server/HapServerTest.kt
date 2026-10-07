package pl.prodevcode.homekit.server

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.prodevcode.homekit.accessory.AccessoryInfo
import pl.prodevcode.homekit.accessory.RemoteKey
import pl.prodevcode.homekit.accessory.TelevisionAccessory
import pl.prodevcode.homekit.accessory.TelevisionControls
import pl.prodevcode.homekit.crypto.Tlv8
import pl.prodevcode.homekit.crypto.TlvError
import pl.prodevcode.homekit.crypto.TlvMethod
import pl.prodevcode.homekit.crypto.TlvType
import pl.prodevcode.homekit.pairing.PairingStore
import pl.prodevcode.homekit.testing.ControllerIdentity
import pl.prodevcode.homekit.testing.FakeController
import pl.prodevcode.testing.FakeSharedPreferences

class HapServerTest {

    private class RecordingControls : TelevisionControls {
        val calls = ArrayList<String>()
        override fun setActive(active: Boolean) { calls += "active=$active" }
        override fun setPlaying(playing: Boolean) { calls += "playing=$playing" }
        override fun remoteKey(key: RemoteKey) { calls += "key=$key" }
        override fun setVolume(percent: Int) { calls += "volume=$percent" }
        override fun volumeStep(up: Boolean) { calls += "step=${if (up) "up" else "down"}" }
        override fun setMuted(muted: Boolean) { calls += "muted=$muted" }
        override fun selectInput(id: Int) { calls += "input=$id" }
        override fun renameInput(id: Int, name: String) { calls += "rename=$id:$name" }
        override fun setInputVisible(id: Int, visible: Boolean) { calls += "visible=$id:$visible" }
    }

    private val prefs = FakeSharedPreferences()
    private val store = PairingStore(prefs)
    private val controls = RecordingControls()
    private val tv = TelevisionAccessory(AccessoryInfo("Salon", "ACME", "TV", "001", "1.2.0"), controls)
    private var paired = 0
    private var unpaired = 0
    private val router = HapRouter(store, tv.accessory, setupCode = { SETUP_CODE }, listener = object : HapRouter.Listener {
        override fun onPaired() { paired++ }
        override fun onUnpaired() { unpaired++ }
        override fun onIdentify() {}
    })
    private val server = HapServer(router)
    private var port = 0

    @Before fun start() { port = server.start() }
    @After fun stop() = server.stop()

    private fun pairedController(identity: ControllerIdentity = ControllerIdentity()): FakeController {
        val c = FakeController(port, identity)
        assertNull(c.pairSetup(SETUP_CODE))
        assertNull(c.pairVerify())
        return c
    }

    @Test fun `pair-setup with the wrong code fails and leaves the accessory unpaired`() {
        FakeController(port).use { c ->
            assertEquals(TlvError.AUTHENTICATION, c.pairSetup("000-00-001"))
        }
        assertFalse(store.isPaired)
        assertEquals(0, paired)
    }

    @Test fun `secure endpoints require a verified session`() {
        FakeController(port).use { c ->
            assertEquals(470, c.get("/accessories").status)
        }
    }

    @Test fun `pairs, verifies and serves the accessory database encrypted`() {
        pairedController().use { c ->
            assertEquals(1, paired)
            assertTrue(store.isPaired)
            assertEquals(store.accessoryId, c.accessoryId)

            val db = c.get("/accessories")
            assertEquals(200, db.status)
            val services = db.json().getJSONArray("accessories").getJSONObject(0).getJSONArray("services")
            val types = (0 until services.length()).map { services.getJSONObject(it).getString("type") }
            assertEquals(listOf("3E", "A2", "D8", "113", "D9"), types)
            val television = services.getJSONObject(2)
            assertTrue(television.getBoolean("primary"))
            assertEquals(2, television.getJSONArray("linked").length())

            // second pairing attempt while paired is refused
            FakeController(port).use { other -> assertEquals(TlvError.UNAVAILABLE, other.pairSetup(SETUP_CODE)) }
        }
    }

    @Test fun `writes reach the controls and reads reflect accessory state`() {
        pairedController().use { c ->
            val active = iid(TelevisionAccessory.Type.TELEVISION, TelevisionAccessory.Type.ACTIVE)
            val remote = iid(TelevisionAccessory.Type.TELEVISION, TelevisionAccessory.Type.REMOTE_KEY)
            val volume = iid(TelevisionAccessory.Type.TELEVISION_SPEAKER, TelevisionAccessory.Type.VOLUME)
            val selector = iid(TelevisionAccessory.Type.TELEVISION_SPEAKER, TelevisionAccessory.Type.VOLUME_SELECTOR)

            val write = c.putJson(
                "/characteristics",
                """{"characteristics":[{"aid":1,"iid":$active,"value":1},{"aid":1,"iid":$remote,"value":11},
                   {"aid":1,"iid":$volume,"value":40},{"aid":1,"iid":$selector,"value":1}]}""",
            )
            assertEquals(204, write.status)
            assertEquals(listOf("active=true", "key=PLAY_PAUSE", "volume=40", "step=down"), controls.calls)

            tv.setActive(true)
            tv.setVolume(55, muted = true)
            val read = c.get("/characteristics?id=1.$active,1.$volume")
            assertEquals(200, read.status)
            val values = read.json().getJSONArray("characteristics")
            assertEquals(1, values.getJSONObject(0).getInt("value"))
            assertEquals(55, values.getJSONObject(1).getInt("value"))

            // write-only characteristic cannot be read, out-of-range value is rejected
            val bad = c.get("/characteristics?id=1.$remote")
            assertEquals(207, bad.status)
            assertEquals(-70405, bad.json().getJSONArray("characteristics").getJSONObject(0).getInt("status"))
            val invalid = c.putJson("/characteristics", """{"characteristics":[{"aid":1,"iid":$volume,"value":500}]}""")
            assertEquals(207, invalid.status)
        }
    }

    @Test fun `subscribed controllers receive events, the writer does not`() {
        val admin = ControllerIdentity()
        pairedController(admin).use { first ->
            val active = iid(TelevisionAccessory.Type.TELEVISION, TelevisionAccessory.Type.ACTIVE)
            assertEquals(204, first.putJson("/characteristics", """{"characteristics":[{"aid":1,"iid":$active,"ev":true}]}""").status)

            tv.setActive(true)
            val event = first.awaitEvent().getJSONArray("characteristics").getJSONObject(0)
            assertEquals(active, event.getInt("iid"))
            assertEquals(1, event.getInt("value"))

            FakeController(port, admin).use { second ->
                assertNull(second.pairVerify(first.accessoryPublicKey))
                assertEquals(204, second.putJson("/characteristics", """{"characteristics":[{"aid":1,"iid":$active,"value":0}]}""").status)
                // the event for the write lands on the first (subscribed) connection only
                assertEquals(0, first.awaitEvent().getJSONArray("characteristics").getJSONObject(0).getInt("value"))
                val probe = second.get("/characteristics?id=1.$active")
                assertEquals("HTTP/1.1", probe.protocol)
            }
        }
    }

    @Test fun `pairings endpoint lists, adds and removes controllers`() {
        pairedController().use { c ->
            val list = c.postTlv("/pairings", Tlv8().put(TlvType.STATE, 1).put(TlvType.METHOD, TlvMethod.LIST_PAIRINGS))
            assertEquals(200, list.status)
            assertEquals(c.identity.id, String(list.tlv()[TlvType.IDENTIFIER]!!))

            val guest = ControllerIdentity("guest")
            val add = Tlv8().put(TlvType.STATE, 1).put(TlvType.METHOD, TlvMethod.ADD_PAIRING)
                .put(TlvType.IDENTIFIER, guest.id).put(TlvType.PUBLIC_KEY, guest.keys.publicKey).put(TlvType.PERMISSIONS, 0)
            assertEquals(2, c.postTlv("/pairings", add).tlv().byte(TlvType.STATE))
            assertEquals(2, store.pairings.value.size)
            assertFalse(store.find("guest")!!.admin)

            // the guest can verify but may not manage pairings
            FakeController(port, guest).use { g ->
                assertNull(g.pairVerify(c.accessoryPublicKey))
                val denied = g.postTlv("/pairings", Tlv8().put(TlvType.STATE, 1).put(TlvType.METHOD, TlvMethod.LIST_PAIRINGS))
                assertEquals(TlvError.AUTHENTICATION, denied.tlv().byte(TlvType.ERROR))
            }

            // removing the only admin drops everyone and reopens pair-setup
            val remove = Tlv8().put(TlvType.STATE, 1).put(TlvType.METHOD, TlvMethod.REMOVE_PAIRING).put(TlvType.IDENTIFIER, c.identity.id)
            assertEquals(2, c.postTlv("/pairings", remove).tlv().byte(TlvType.STATE))
            assertFalse(store.isPaired)
            assertEquals(1, unpaired)
        }
        FakeController(port).use { fresh -> assertNull(fresh.pairSetup(SETUP_CODE)) }
    }

    @Test fun `identity survives a restart so paired controllers can verify again`() {
        val identity = ControllerIdentity()
        val accessoryKey = pairedController(identity).use { it.accessoryPublicKey }
        server.stop()
        val restarted = HapServer(HapRouter(PairingStore(prefs), tv.accessory, { SETUP_CODE }, object : HapRouter.Listener {
            override fun onPaired() {}
            override fun onUnpaired() {}
            override fun onIdentify() {}
        }))
        val port2 = restarted.start()
        try {
            FakeController(port2, identity).use { c ->
                assertNull(c.pairVerify(accessoryKey))
                assertEquals(200, c.get("/accessories").status)
            }
        } finally { restarted.stop() }
    }

    private fun iid(service: String, characteristic: String): Int =
        tv.accessory.services.first { it.type == service }.characteristic(characteristic).iid

    private companion object { const val SETUP_CODE = "031-45-154" }
}
