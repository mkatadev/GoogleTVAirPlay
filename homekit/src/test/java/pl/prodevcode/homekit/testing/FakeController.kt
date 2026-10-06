package pl.prodevcode.homekit.testing

import java.net.Socket
import org.json.JSONObject
import pl.prodevcode.homekit.crypto.HapCrypto
import pl.prodevcode.homekit.crypto.Tlv8
import pl.prodevcode.homekit.crypto.TlvMethod
import pl.prodevcode.homekit.crypto.TlvType
import pl.prodevcode.homekit.pairing.Pairing
import pl.prodevcode.homekit.pairing.PairSetup
import pl.prodevcode.homekit.pairing.SessionKeys
import pl.prodevcode.homekit.server.SecureChannel

/** A controller identity (what an iPhone keeps in its keychain) reusable across connections. */
class ControllerIdentity(val id: String = "11111111-2222-3333-4444-555555555555") {
    val keys = HapCrypto.Ed25519KeyPair.generate()
}

/** Minimal HAP controller speaking to a [pl.prodevcode.homekit.server.HapServer] on localhost. */
class FakeController(port: Int, val identity: ControllerIdentity = ControllerIdentity()) : AutoCloseable {

    class Response(val protocol: String, val status: Int, val body: ByteArray) {
        fun json() = JSONObject(String(body))
        fun tlv() = Tlv8.decode(body)
    }

    private val socket = Socket("127.0.0.1", port).apply { soTimeout = 5_000 }
    private val input = socket.getInputStream()
    private val output = socket.getOutputStream()
    private var channel: SecureChannel? = null
    private var pending = ByteArray(0)

    lateinit var accessoryId: String
        private set
    lateinit var accessoryPublicKey: ByteArray
        private set

    fun request(method: String, path: String, body: ByteArray = ByteArray(0), contentType: String? = null): Response {
        val head = StringBuilder("$method $path HTTP/1.1\r\nHost: localhost\r\n")
        contentType?.let { head.append("Content-Type: $it\r\n") }
        head.append("Content-Length: ${body.size}\r\n\r\n")
        val bytes = head.toString().toByteArray() + body
        output.write(channel?.encrypt(bytes) ?: bytes)
        output.flush()
        return readMessage()
    }

    fun get(path: String) = request("GET", path)
    fun putJson(path: String, json: String) = request("PUT", path, json.toByteArray(), "application/hap+json")
    fun postTlv(path: String, tlv: Tlv8) = request("POST", path, tlv.encode(), "application/pairing+tlv8")

    /** Full pair-setup M1–M6 with the given setup code; returns the accessory's error code if it refused. */
    fun pairSetup(setupCode: String): Int? {
        val m2 = postTlv("/pair-setup", Tlv8().put(TlvType.STATE, 1).put(TlvType.METHOD, TlvMethod.PAIR_SETUP)).tlv()
        m2.byte(TlvType.ERROR)?.let { return it }
        val srp = SrpClient(PairSetup.SRP_USERNAME, setupCode)
        val proof = srp.proof(m2[TlvType.SALT]!!, m2[TlvType.PUBLIC_KEY]!!)
        val m4 = postTlv("/pair-setup", Tlv8().put(TlvType.STATE, 3).put(TlvType.PUBLIC_KEY, srp.publicKey).put(TlvType.PROOF, proof)).tlv()
        m4.byte(TlvType.ERROR)?.let { return it }
        check(srp.expectedServerProof(proof).contentEquals(m4[TlvType.PROOF])) { "server proof mismatch" }

        val k = srp.sessionKey
        val controllerX = HapCrypto.hkdfSha512(k, "Pair-Setup-Controller-Sign-Salt", "Pair-Setup-Controller-Sign-Info")
        val idBytes = identity.id.toByteArray()
        val sig = HapCrypto.ed25519Sign(identity.keys.privateKey, controllerX + idBytes + identity.keys.publicKey)
        val sub = Tlv8().put(TlvType.IDENTIFIER, idBytes).put(TlvType.PUBLIC_KEY, identity.keys.publicKey).put(TlvType.SIGNATURE, sig).encode()
        val key = HapCrypto.hkdfSha512(k, "Pair-Setup-Encrypt-Salt", "Pair-Setup-Encrypt-Info")
        val m6 = postTlv(
            "/pair-setup",
            Tlv8().put(TlvType.STATE, 5).put(TlvType.ENCRYPTED_DATA, HapCrypto.chachaEncrypt(key, HapCrypto.nonce("PS-Msg05"), sub)),
        ).tlv()
        m6.byte(TlvType.ERROR)?.let { return it }
        val plain = HapCrypto.chachaDecrypt(key, HapCrypto.nonce("PS-Msg06"), m6[TlvType.ENCRYPTED_DATA]!!)!!
        val accessory = Tlv8.decode(plain)
        accessoryId = String(accessory[TlvType.IDENTIFIER]!!)
        accessoryPublicKey = accessory[TlvType.PUBLIC_KEY]!!
        val accessoryX = HapCrypto.hkdfSha512(k, "Pair-Setup-Accessory-Sign-Salt", "Pair-Setup-Accessory-Sign-Info")
        check(HapCrypto.ed25519Verify(accessoryPublicKey, accessory[TlvType.SIGNATURE]!!, accessoryX + accessoryId.toByteArray() + accessoryPublicKey))
        return null
    }

    /** Pair-verify M1–M4; switches this connection to encrypted framing on success. */
    fun pairVerify(accessoryPublicKey: ByteArray = this.accessoryPublicKey): Int? {
        val ours = HapCrypto.X25519KeyPair.generate()
        val m2 = postTlv("/pair-verify", Tlv8().put(TlvType.STATE, 1).put(TlvType.PUBLIC_KEY, ours.publicKey)).tlv()
        m2.byte(TlvType.ERROR)?.let { return it }
        val theirs = m2[TlvType.PUBLIC_KEY]!!
        val shared = ours.sharedSecret(theirs)!!
        val key = HapCrypto.hkdfSha512(shared, "Pair-Verify-Encrypt-Salt", "Pair-Verify-Encrypt-Info")
        val sub = Tlv8.decode(HapCrypto.chachaDecrypt(key, HapCrypto.nonce("PV-Msg02"), m2[TlvType.ENCRYPTED_DATA]!!)!!)
        val accId = sub[TlvType.IDENTIFIER]!!
        check(HapCrypto.ed25519Verify(accessoryPublicKey, sub[TlvType.SIGNATURE]!!, theirs + accId + ours.publicKey)) { "accessory signature" }

        val idBytes = identity.id.toByteArray()
        val sig = HapCrypto.ed25519Sign(identity.keys.privateKey, ours.publicKey + idBytes + theirs)
        val m3 = Tlv8().put(TlvType.IDENTIFIER, idBytes).put(TlvType.SIGNATURE, sig).encode()
        val m4 = postTlv(
            "/pair-verify",
            Tlv8().put(TlvType.STATE, 3).put(TlvType.ENCRYPTED_DATA, HapCrypto.chachaEncrypt(key, HapCrypto.nonce("PV-Msg03"), m3)),
        ).tlv()
        m4.byte(TlvType.ERROR)?.let { return it }
        // the controller encrypts with the accessory's read key and decrypts with its write key
        channel = SecureChannel(
            SessionKeys(
                controllerToAccessory = HapCrypto.hkdfSha512(shared, "Control-Salt", "Control-Read-Encryption-Key"),
                accessoryToController = HapCrypto.hkdfSha512(shared, "Control-Salt", "Control-Write-Encryption-Key"),
                controller = Pairing(identity.id, identity.keys.publicKey, admin = true),
            ),
        )
        return null
    }

    /** Blocks for the next unsolicited `EVENT/1.0` message. */
    fun awaitEvent(): JSONObject {
        val msg = readMessage()
        check(msg.protocol == "EVENT/1.0") { "expected event, got ${msg.protocol} ${msg.status}" }
        return msg.json()
    }

    private fun readMessage(): Response {
        val buf = ByteArray(4096)
        while (true) {
            parse()?.let { return it }
            val n = input.read(buf)
            check(n >= 0) { "connection closed" }
            val chunk = buf.copyOf(n)
            pending += channel?.decrypt(chunk) ?: chunk
        }
    }

    private fun parse(): Response? {
        val text = String(pending, Charsets.ISO_8859_1)
        val headerEnd = text.indexOf("\r\n\r\n")
        if (headerEnd < 0) return null
        val lines = text.substring(0, headerEnd).split("\r\n")
        val status = lines[0].split(' ')
        val length = lines.drop(1).firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toInt() ?: 0
        val bodyStart = headerEnd + 4
        if (pending.size < bodyStart + length) return null
        val body = pending.copyOfRange(bodyStart, bodyStart + length)
        pending = pending.copyOfRange(bodyStart + length, pending.size)
        return Response(status[0], status[1].toInt(), body)
    }

    override fun close() = socket.close()
}
