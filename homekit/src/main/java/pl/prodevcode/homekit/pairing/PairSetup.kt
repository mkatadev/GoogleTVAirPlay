package pl.prodevcode.homekit.pairing

import pl.prodevcode.homekit.crypto.HapCrypto
import pl.prodevcode.homekit.crypto.SrpServer
import pl.prodevcode.homekit.crypto.Tlv8
import pl.prodevcode.homekit.crypto.TlvError
import pl.prodevcode.homekit.crypto.TlvType

/**
 * Pair-setup state machine (HAP 5.6) for one connection: SRP with the setup code, then an
 * exchange of long-term Ed25519 keys. The controller that completes it becomes an admin pairing.
 */
class PairSetup(
    private val store: PairingStore,
    private val setupCode: () -> String,
    private val onPaired: (Pairing) -> Unit,
) {
    private var srp: SrpServer? = null
    private var sessionKey: ByteArray? = null

    fun handle(body: ByteArray): ByteArray {
        val tlv = runCatching { Tlv8.decode(body) }.getOrNull() ?: return Tlv8.error(2, TlvError.UNKNOWN)
        return when (tlv.byte(TlvType.STATE)) {
            1 -> m2()
            3 -> m4(tlv)
            5 -> m6(tlv)
            else -> Tlv8.error(2, TlvError.UNKNOWN)
        }
    }

    private fun m2(): ByteArray {
        if (store.isPaired) return Tlv8.error(2, TlvError.UNAVAILABLE)
        val server = SrpServer(SRP_USERNAME, setupCode())
        srp = server
        return Tlv8().put(TlvType.STATE, 2).put(TlvType.SALT, server.salt).put(TlvType.PUBLIC_KEY, server.publicKey).encode()
    }

    private fun m4(tlv: Tlv8): ByteArray {
        val server = srp ?: return Tlv8.error(4, TlvError.UNKNOWN)
        val a = tlv[TlvType.PUBLIC_KEY] ?: return Tlv8.error(4, TlvError.UNKNOWN)
        val proof = tlv[TlvType.PROOF] ?: return Tlv8.error(4, TlvError.UNKNOWN)
        val m2 = server.verifyClientProof(a, proof) ?: run {
            srp = null
            return Tlv8.error(4, TlvError.AUTHENTICATION)
        }
        sessionKey = HapCrypto.hkdfSha512(server.sessionKey!!, "Pair-Setup-Encrypt-Salt", "Pair-Setup-Encrypt-Info")
        return Tlv8().put(TlvType.STATE, 4).put(TlvType.PROOF, m2).encode()
    }

    private fun m6(tlv: Tlv8): ByteArray {
        val server = srp ?: return Tlv8.error(6, TlvError.UNKNOWN)
        val key = sessionKey ?: return Tlv8.error(6, TlvError.UNKNOWN)
        val srpKey = server.sessionKey ?: return Tlv8.error(6, TlvError.UNKNOWN)
        val encrypted = tlv[TlvType.ENCRYPTED_DATA] ?: return Tlv8.error(6, TlvError.UNKNOWN)
        val plain = HapCrypto.chachaDecrypt(key, HapCrypto.nonce("PS-Msg05"), encrypted)
            ?: return Tlv8.error(6, TlvError.AUTHENTICATION)
        val sub = runCatching { Tlv8.decode(plain) }.getOrNull() ?: return Tlv8.error(6, TlvError.UNKNOWN)
        val controllerId = sub[TlvType.IDENTIFIER] ?: return Tlv8.error(6, TlvError.UNKNOWN)
        val controllerLtpk = sub[TlvType.PUBLIC_KEY] ?: return Tlv8.error(6, TlvError.UNKNOWN)
        val signature = sub[TlvType.SIGNATURE] ?: return Tlv8.error(6, TlvError.UNKNOWN)

        val controllerX = HapCrypto.hkdfSha512(srpKey, "Pair-Setup-Controller-Sign-Salt", "Pair-Setup-Controller-Sign-Info")
        if (!HapCrypto.ed25519Verify(controllerLtpk, signature, controllerX + controllerId + controllerLtpk)) {
            return Tlv8.error(6, TlvError.AUTHENTICATION)
        }

        val accessoryX = HapCrypto.hkdfSha512(srpKey, "Pair-Setup-Accessory-Sign-Salt", "Pair-Setup-Accessory-Sign-Info")
        val accessoryId = store.accessoryId.toByteArray()
        val accessorySig = HapCrypto.ed25519Sign(store.keyPair.privateKey, accessoryX + accessoryId + store.keyPair.publicKey)
        val response = Tlv8()
            .put(TlvType.IDENTIFIER, accessoryId)
            .put(TlvType.PUBLIC_KEY, store.keyPair.publicKey)
            .put(TlvType.SIGNATURE, accessorySig)
            .encode()
        val sealed = HapCrypto.chachaEncrypt(key, HapCrypto.nonce("PS-Msg06"), response)

        val pairing = Pairing(String(controllerId), controllerLtpk, admin = true)
        store.add(pairing)
        onPaired(pairing)
        srp = null
        sessionKey = null
        return Tlv8().put(TlvType.STATE, 6).put(TlvType.ENCRYPTED_DATA, sealed).encode()
    }

    companion object {
        const val SRP_USERNAME = "Pair-Setup"
    }
}
