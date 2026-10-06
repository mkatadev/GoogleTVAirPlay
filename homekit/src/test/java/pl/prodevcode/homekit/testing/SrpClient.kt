package pl.prodevcode.homekit.testing

import java.math.BigInteger
import java.security.SecureRandom
import pl.prodevcode.homekit.crypto.SrpServer
import pl.prodevcode.homekit.crypto.SrpServer.Companion.G
import pl.prodevcode.homekit.crypto.SrpServer.Companion.N
import pl.prodevcode.homekit.crypto.SrpServer.Companion.pad
import pl.prodevcode.homekit.crypto.SrpServer.Companion.sha512
import pl.prodevcode.homekit.crypto.SrpServer.Companion.xor

/** SRP-6a client with the same padding conventions as the server, i.e. what an iOS controller computes. */
class SrpClient(private val username: String, private val password: String) {
    private val a = BigInteger(1, SecureRandom().generateSeed(32))
    val publicKey: ByteArray = pad(G.modPow(a, N))
    lateinit var sessionKey: ByteArray
        private set

    fun proof(salt: ByteArray, serverPublicKey: ByteArray): ByteArray {
        val b = BigInteger(1, serverPublicKey)
        val u = BigInteger(1, sha512(publicKey, serverPublicKey))
        val x = BigInteger(1, sha512(salt, sha512("$username:$password".toByteArray())))
        val k = BigInteger(1, sha512(pad(N), pad(G)))
        val base = b.subtract(k.multiply(G.modPow(x, N))).mod(N)
        val s = base.modPow(a.add(u.multiply(x)), N)
        sessionKey = sha512(pad(s))
        return sha512(
            xor(sha512(pad(N)), sha512(G.toByteArray())),
            sha512(username.toByteArray()),
            salt, publicKey, serverPublicKey, sessionKey,
        )
    }

    fun expectedServerProof(clientProof: ByteArray): ByteArray = sha512(publicKey, clientProof, sessionKey)
}
