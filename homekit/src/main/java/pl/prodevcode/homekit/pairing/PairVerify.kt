package pl.prodevcode.homekit.pairing

import pl.prodevcode.homekit.crypto.HapCrypto
import pl.prodevcode.homekit.crypto.Tlv8
import pl.prodevcode.homekit.crypto.TlvError
import pl.prodevcode.homekit.crypto.TlvType

/** Keys of an established HAP session, one per direction. */
class SessionKeys(val controllerToAccessory: ByteArray, val accessoryToController: ByteArray, val controller: Pairing)

/**
 * Pair-verify state machine (HAP 5.7): Curve25519 key agreement authenticated with the
 * long-term Ed25519 keys exchanged during pair-setup.
 */
class PairVerify(private val store: PairingStore) {

    private var ephemeral: HapCrypto.X25519KeyPair? = null
    private var controllerPublicKey: ByteArray? = null
    private var sharedSecret: ByteArray? = null
    private var sessionKey: ByteArray? = null

    /** Set after M4; the connection switches to encrypted framing from the next request on. */
    var established: SessionKeys? = null
        private set

    fun handle(body: ByteArray): ByteArray {
        val tlv = runCatching { Tlv8.decode(body) }.getOrNull() ?: return Tlv8.error(2, TlvError.UNKNOWN)
        return when (tlv.byte(TlvType.STATE)) {
            1 -> m2(tlv)
            3 -> m4(tlv)
            else -> Tlv8.error(2, TlvError.UNKNOWN)
        }
    }

    private fun m2(tlv: Tlv8): ByteArray {
        val controllerPk = tlv[TlvType.PUBLIC_KEY]?.takeIf { it.size == 32 } ?: return Tlv8.error(2, TlvError.UNKNOWN)
        val ours = HapCrypto.X25519KeyPair.generate()
        val shared = ours.sharedSecret(controllerPk) ?: return Tlv8.error(2, TlvError.AUTHENTICATION)
        val accessoryId = store.accessoryId.toByteArray()
        val signature = HapCrypto.ed25519Sign(store.keyPair.privateKey, ours.publicKey + accessoryId + controllerPk)
        val sub = Tlv8().put(TlvType.IDENTIFIER, accessoryId).put(TlvType.SIGNATURE, signature).encode()
        val key = HapCrypto.hkdfSha512(shared, "Pair-Verify-Encrypt-Salt", "Pair-Verify-Encrypt-Info")
        val sealed = HapCrypto.chachaEncrypt(key, HapCrypto.nonce("PV-Msg02"), sub)

        ephemeral = ours
        controllerPublicKey = controllerPk
        sharedSecret = shared
        sessionKey = key
        return Tlv8().put(TlvType.STATE, 2).put(TlvType.PUBLIC_KEY, ours.publicKey).put(TlvType.ENCRYPTED_DATA, sealed).encode()
    }

    private fun m4(tlv: Tlv8): ByteArray {
        val ours = ephemeral ?: return Tlv8.error(4, TlvError.UNKNOWN)
        val controllerPk = controllerPublicKey ?: return Tlv8.error(4, TlvError.UNKNOWN)
        val shared = sharedSecret ?: return Tlv8.error(4, TlvError.UNKNOWN)
        val key = sessionKey ?: return Tlv8.error(4, TlvError.UNKNOWN)
        val encrypted = tlv[TlvType.ENCRYPTED_DATA] ?: return Tlv8.error(4, TlvError.UNKNOWN)
        val plain = HapCrypto.chachaDecrypt(key, HapCrypto.nonce("PV-Msg03"), encrypted)
            ?: return Tlv8.error(4, TlvError.AUTHENTICATION)
        val sub = runCatching { Tlv8.decode(plain) }.getOrNull() ?: return Tlv8.error(4, TlvError.UNKNOWN)
        val controllerId = sub[TlvType.IDENTIFIER]?.let { String(it) } ?: return Tlv8.error(4, TlvError.UNKNOWN)
        val signature = sub[TlvType.SIGNATURE] ?: return Tlv8.error(4, TlvError.UNKNOWN)
        val pairing = store.find(controllerId) ?: return Tlv8.error(4, TlvError.AUTHENTICATION)
        if (!HapCrypto.ed25519Verify(pairing.publicKey, signature, controllerPk + controllerId.toByteArray() + ours.publicKey)) {
            return Tlv8.error(4, TlvError.AUTHENTICATION)
        }
        established = SessionKeys(
            controllerToAccessory = HapCrypto.hkdfSha512(shared, "Control-Salt", "Control-Write-Encryption-Key"),
            accessoryToController = HapCrypto.hkdfSha512(shared, "Control-Salt", "Control-Read-Encryption-Key"),
            controller = pairing,
        )
        ephemeral = null; controllerPublicKey = null; sharedSecret = null; sessionKey = null
        return Tlv8().put(TlvType.STATE, 4).encode()
    }
}
