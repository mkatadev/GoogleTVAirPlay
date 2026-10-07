package pl.prodevcode.homekit.accessory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelevisionAccessoryTest {

    private val calls = ArrayList<String>()
    private val controls = object : TelevisionControls {
        override fun setActive(active: Boolean) {}
        override fun setPlaying(playing: Boolean) {}
        override fun remoteKey(key: RemoteKey) {}
        override fun setVolume(percent: Int) {}
        override fun volumeStep(up: Boolean) {}
        override fun setMuted(muted: Boolean) {}
        override fun selectInput(id: Int) { calls += "select=$id" }
        override fun renameInput(id: Int, name: String) { calls += "rename=$id:$name" }
        override fun setInputVisible(id: Int, visible: Boolean) { calls += "visible=$id:$visible" }
    }
    private val info = AccessoryInfo("TV", "ACME", "Box", "1", "1.0.0")
    private val tv = TelevisionAccessory(info, controls)

    private fun television() = tv.accessory.services.first { it.type == TelevisionAccessory.Type.TELEVISION }
    private fun inputServices() = tv.accessory.services.filter { it.type == TelevisionAccessory.Type.INPUT_SOURCE }
    private fun input(id: Int) = inputServices().first { it.characteristic(TelevisionAccessory.Type.IDENTIFIER).value == id.toLong() }

    @Test fun `starts with the AirPlay input linked to the television`() {
        assertEquals(listOf(TelevisionAccessory.DEFAULT_INPUT), tv.inputs)
        val linked = television().toJson(withMeta = false).getJSONArray("linked")
        assertTrue((0 until linked.length()).map { linked.getInt(it) }.contains(input(1).iid))
        assertEquals(1L, television().characteristic(TelevisionAccessory.Type.ACTIVE_IDENTIFIER).value)
    }

    @Test fun `adding inputs rebuilds the database with stable iids and fires the callback`() {
        var rebuilt: Accessory? = null
        tv.onDatabaseChanged = { rebuilt = it }
        val airplayIid = input(1).iid
        val speakerIid = tv.accessory.services.first { it.type == TelevisionAccessory.Type.TELEVISION_SPEAKER }.iid

        tv.setInputs(listOf(TvInput(1, "AirPlay", InputType.AIRPLAY), TvInput(2, "Google TV", InputType.HOME_SCREEN), TvInput(7, "YouTube", InputType.APPLICATION)))

        assertNotNull(rebuilt)
        assertEquals(3, inputServices().size)
        assertEquals(airplayIid, input(1).iid)
        assertEquals(speakerIid, tv.accessory.services.first { it.type == TelevisionAccessory.Type.TELEVISION_SPEAKER }.iid)
        assertTrue(input(7).iid > input(2).iid)
        val valid = television().characteristic(TelevisionAccessory.Type.ACTIVE_IDENTIFIER).toJson(withMeta = true).getJSONArray("valid-values")
        assertEquals(listOf(1, 2, 7), (0 until valid.length()).map { valid.getInt(it) })
        // JSON of the whole accessory must still be valid (unique iids)
        assertTrue(tv.accessory.toJson().toString().contains("\"iid\":${input(7).iid}"))
    }

    @Test fun `same set of inputs updates names and visibility in place without a rebuild`() {
        tv.setInputs(listOf(TvInput(1, "AirPlay", InputType.AIRPLAY), TvInput(5, "Netflix", InputType.APPLICATION)))
        var rebuilt = false
        tv.onDatabaseChanged = { rebuilt = true }
        val before = tv.accessory

        tv.setInputs(listOf(TvInput(1, "AirPlay", InputType.AIRPLAY), TvInput(5, "Netflix PL", InputType.APPLICATION, visible = false)))

        assertFalse(rebuilt)
        assertTrue(before === tv.accessory)
        assertEquals("Netflix PL", input(5).characteristic(TelevisionAccessory.Type.CONFIGURED_NAME).value)
        assertEquals(1L, input(5).characteristic(TelevisionAccessory.Type.CURRENT_VISIBILITY_STATE).value)
        assertEquals(1L, input(5).characteristic(TelevisionAccessory.Type.TARGET_VISIBILITY_STATE).value)
    }

    @Test fun `removing the active input falls back to the first one`() {
        tv.setInputs(listOf(TvInput(1, "AirPlay", InputType.AIRPLAY), TvInput(9, "Max", InputType.APPLICATION)))
        tv.setActiveInput(9)
        assertEquals(9L, television().characteristic(TelevisionAccessory.Type.ACTIVE_IDENTIFIER).value)

        tv.setInputs(listOf(TvInput(1, "AirPlay", InputType.AIRPLAY)))
        assertEquals(1L, television().characteristic(TelevisionAccessory.Type.ACTIVE_IDENTIFIER).value)
        tv.setActiveInput(9) // unknown ids are ignored
        assertEquals(1L, television().characteristic(TelevisionAccessory.Type.ACTIVE_IDENTIFIER).value)
    }

    @Test fun `controller writes reach the controls`() {
        tv.setInputs(listOf(TvInput(1, "AirPlay", InputType.AIRPLAY), TvInput(3, "YouTube", InputType.APPLICATION)))
        assertEquals(HapStatus.SUCCESS, television().characteristic(TelevisionAccessory.Type.ACTIVE_IDENTIFIER).write(3))
        assertEquals(HapStatus.INVALID_VALUE, television().characteristic(TelevisionAccessory.Type.ACTIVE_IDENTIFIER).write(4))
        assertEquals(HapStatus.SUCCESS, input(3).characteristic(TelevisionAccessory.Type.CONFIGURED_NAME).write("Tube"))
        assertEquals(HapStatus.SUCCESS, input(3).characteristic(TelevisionAccessory.Type.TARGET_VISIBILITY_STATE).write(1))
        assertEquals(listOf("select=3", "rename=3:Tube", "visible=3:false"), calls)
        assertNull(tv.accessory.characteristic(99_999))
    }
}
