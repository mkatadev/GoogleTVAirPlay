package pl.prodevcode.homekit.crypto

import com.google.crypto.tink.subtle.Ed25519Sign
import com.google.crypto.tink.subtle.Ed25519Verify
import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.subtle.X25519
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The handful of primitives HAP needs, behind one small surface so the protocol code stays readable. */
object HapCrypto {

    fun hkdfSha512(ikm: ByteArray, salt: String, info: String, length: Int = 32): ByteArray =
        Hkdf.computeHkdf("HMACSHA512", ikm, salt.toByteArray(), info.toByteArray(), length)

    /** 96-bit nonce: 4 zero bytes followed by the little-endian 64-bit counter, as HAP frames require. */
    fun nonce(counter: Long): ByteArray {
        val n = ByteArray(12)
        for (i in 0 until 8) n[4 + i] = (counter ushr (8 * i)).toByte()
        return n
    }

    fun nonce(label: String): ByteArray {
        val bytes = label.toByteArray()
        require(bytes.size <= 12)
        return ByteArray(12 - bytes.size) + bytes
    }

    fun chachaEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): ByteArray =
        chacha(Cipher.ENCRYPT_MODE, key, nonce, plaintext, aad)

    /** Returns `null` when the tag does not verify. */
    fun chachaDecrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray? = null): ByteArray? =
        try { chacha(Cipher.DECRYPT_MODE, key, nonce, ciphertext, aad) } catch (_: GeneralSecurityException) { null }

    private fun chacha(mode: Int, key: ByteArray, nonce: ByteArray, input: ByteArray, aad: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        cipher.init(mode, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce))
        aad?.let(cipher::updateAAD)
        return cipher.doFinal(input)
    }

    class Ed25519KeyPair(val privateKey: ByteArray, val publicKey: ByteArray) {
        companion object {
            fun generate(): Ed25519KeyPair = Ed25519Sign.KeyPair.newKeyPair().let { Ed25519KeyPair(it.privateKey, it.publicKey) }
        }
    }

    fun ed25519Sign(privateKey: ByteArray, message: ByteArray): ByteArray = Ed25519Sign(privateKey).sign(message)

    fun ed25519Verify(publicKey: ByteArray, signature: ByteArray, message: ByteArray): Boolean =
        try { Ed25519Verify(publicKey).verify(signature, message); true } catch (_: GeneralSecurityException) { false }

    class X25519KeyPair(val privateKey: ByteArray, val publicKey: ByteArray) {
        fun sharedSecret(peerPublicKey: ByteArray): ByteArray? =
            try { X25519.computeSharedSecret(privateKey, peerPublicKey) } catch (_: GeneralSecurityException) { null }

        companion object {
            fun generate(): X25519KeyPair = X25519.generatePrivateKey().let { X25519KeyPair(it, X25519.publicFromPrivate(it)) }
        }
    }
}
