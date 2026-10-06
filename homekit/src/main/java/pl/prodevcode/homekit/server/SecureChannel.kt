package pl.prodevcode.homekit.server

import pl.prodevcode.homekit.crypto.HapCrypto
import pl.prodevcode.homekit.pairing.SessionKeys

/**
 * HAP session framing (HAP 6.5.2): every frame is a 2-byte little-endian plaintext length
 * (≤ 1024), the ChaCha20-Poly1305 ciphertext with that length as AAD, and the 16-byte tag.
 * Nonces are per-direction frame counters.
 */
class SecureChannel(private val keys: SessionKeys) {
    private var inCounter = 0L
    private var outCounter = 0L
    private var pending = ByteArray(0)

    /** Feeds raw socket bytes; returns the plaintext of every frame completed so far. */
    fun decrypt(data: ByteArray): ByteArray? {
        pending += data
        var out = ByteArray(0)
        while (pending.size >= 2) {
            val len = (pending[0].toInt() and 0xFF) or ((pending[1].toInt() and 0xFF) shl 8)
            if (pending.size < 2 + len + TAG) break
            val aad = pending.copyOfRange(0, 2)
            val cipher = pending.copyOfRange(2, 2 + len + TAG)
            val plain = HapCrypto.chachaDecrypt(keys.controllerToAccessory, HapCrypto.nonce(inCounter++), cipher, aad)
                ?: return null
            out += plain
            pending = pending.copyOfRange(2 + len + TAG, pending.size)
        }
        return out
    }

    fun encrypt(plain: ByteArray): ByteArray {
        var out = ByteArray(0)
        var offset = 0
        do {
            val len = minOf(MAX_FRAME, plain.size - offset)
            val aad = byteArrayOf(len.toByte(), (len shr 8).toByte())
            val chunk = plain.copyOfRange(offset, offset + len)
            out += aad + HapCrypto.chachaEncrypt(keys.accessoryToController, HapCrypto.nonce(outCounter++), chunk, aad)
            offset += len
        } while (offset < plain.size)
        return out
    }

    private companion object {
        const val MAX_FRAME = 1024
        const val TAG = 16
    }
}
