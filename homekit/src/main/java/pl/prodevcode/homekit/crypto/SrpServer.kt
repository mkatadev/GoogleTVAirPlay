package pl.prodevcode.homekit.crypto

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * SRP-6a server side as used by HAP pair-setup: 3072-bit group from RFC 5054, SHA-512,
 * all group elements padded to the modulus length when hashed (matches corecutils / HAP-NodeJS).
 */
class SrpServer(
    private val username: String,
    password: String,
    val salt: ByteArray = SecureRandom().generateSeed(16),
    privateKey: ByteArray = SecureRandom().generateSeed(32),
) {
    private val verifier: BigInteger = verifier(username, password, salt)
    private val b = BigInteger(1, privateKey)

    /** Server public key B = k·v + g^b, padded to the modulus length. */
    val publicKey: ByteArray = pad((K_MULTIPLIER.multiply(verifier).add(G.modPow(b, N))).mod(N))

    /** Session key K after a successful [verifyClientProof]. */
    var sessionKey: ByteArray? = null
        private set

    /**
     * Checks the client's proof M1 for its public key A; returns the server proof M2 or `null`
     * if the client used a different password or an illegal A.
     */
    fun verifyClientProof(clientPublicKey: ByteArray, clientProof: ByteArray): ByteArray? {
        val a = BigInteger(1, clientPublicKey)
        if (a.mod(N) == BigInteger.ZERO) return null
        val aPadded = pad(a)
        val u = BigInteger(1, sha512(aPadded, publicKey))
        if (u == BigInteger.ZERO) return null
        val s = a.multiply(verifier.modPow(u, N)).mod(N).modPow(b, N)
        val k = sha512(pad(s))
        val expected = sha512(
            xor(sha512(pad(N)), sha512(G.toByteArray())),
            sha512(username.toByteArray()),
            salt, aPadded, publicKey, k,
        )
        if (!MessageDigest.isEqual(expected, clientProof)) return null
        sessionKey = k
        return sha512(aPadded, clientProof, k)
    }

    companion object {
        internal val N = BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B" +
                "302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE6" +
                "49286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB9ED529077096966D" +
                "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3BE39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF695581718" +
                "3995497CEA956AE515D2261898FA051015728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64ECFB850458DBEF0A8AEA71575D060C7D" +
                "B3970F85A6E1E4C7ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6BF12FFA06D98A0864D87602733EC86A64521F2B18177B200C" +
                "BBE117577A615D6C770988C0BAD946E208E24FA074E5AB3143DB5BFCE0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF",
            16,
        )
        internal val G: BigInteger = BigInteger.valueOf(5)
        private val N_BYTES = (N.bitLength() + 7) / 8
        private val K_MULTIPLIER = BigInteger(1, sha512(pad(N), pad(G)))

        fun sha512(vararg parts: ByteArray): ByteArray {
            val md = MessageDigest.getInstance("SHA-512")
            parts.forEach(md::update)
            return md.digest()
        }

        internal fun pad(n: BigInteger): ByteArray {
            val raw = n.toByteArray()
            val start = if (raw.size > N_BYTES) raw.size - N_BYTES else 0
            val out = ByteArray(N_BYTES)
            System.arraycopy(raw, start, out, N_BYTES - (raw.size - start), raw.size - start)
            return out
        }

        internal fun xor(a: ByteArray, b: ByteArray) = ByteArray(a.size) { (a[it].toInt() xor b[it].toInt()).toByte() }

        private fun verifier(username: String, password: String, salt: ByteArray): BigInteger {
            val x = BigInteger(1, sha512(salt, sha512("$username:$password".toByteArray())))
            return G.modPow(x, N)
        }
    }
}
