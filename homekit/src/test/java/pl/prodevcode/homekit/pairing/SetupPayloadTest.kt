package pl.prodevcode.homekit.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupPayloadTest {

    @Test fun `uri encodes category, IP flag and code in base36 with the setup id appended`() {
        val uri = SetupPayload.uri("031-45-154", "7OSX", category = 31)
        assertTrue(uri.startsWith("X-HM://"))
        val encoded = uri.removePrefix("X-HM://")
        assertEquals(13, encoded.length)
        assertEquals("7OSX", encoded.takeLast(4))
        val payload = encoded.dropLast(4).toLong(36)
        assertEquals(3145154L, payload and 0x7FFFFFF)
        assertEquals(2L, (payload shr 27) and 0xF)
        assertEquals(31L, (payload shr 31) and 0xFF)
        assertEquals(0L, payload shr 39)
    }

    @Test fun `setup hash is 4 bytes of sha512 in base64`() {
        val sh = SetupPayload.setupHash("7OSX", "65:46:3B:35:79:3E")
        assertEquals(8, sh.length)
        assertEquals(sh, SetupPayload.setupHash("7OSX", "65:46:3B:35:79:3E"))
    }
}
