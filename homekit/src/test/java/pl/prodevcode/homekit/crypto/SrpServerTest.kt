package pl.prodevcode.homekit.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import pl.prodevcode.homekit.testing.SrpClient

class SrpServerTest {

    @Test fun `client with the right code agrees on the session key`() {
        val server = SrpServer("Pair-Setup", "031-45-154")
        val client = SrpClient("Pair-Setup", "031-45-154")
        assertEquals(384, server.publicKey.size)

        val m1 = client.proof(server.salt, server.publicKey)
        val m2 = server.verifyClientProof(client.publicKey, m1)
        assertNotNull(m2)
        assertArrayEquals(client.expectedServerProof(m1), m2)
        assertArrayEquals(client.sessionKey, server.sessionKey)
    }

    @Test fun `wrong code is rejected`() {
        val server = SrpServer("Pair-Setup", "031-45-154")
        val client = SrpClient("Pair-Setup", "031-45-155")
        assertNull(server.verifyClientProof(client.publicKey, client.proof(server.salt, server.publicKey)))
        assertNull(server.sessionKey)
    }

    @Test fun `zero public key is rejected`() {
        val server = SrpServer("Pair-Setup", "031-45-154")
        assertNull(server.verifyClientProof(ByteArray(384), ByteArray(64)))
    }
}
