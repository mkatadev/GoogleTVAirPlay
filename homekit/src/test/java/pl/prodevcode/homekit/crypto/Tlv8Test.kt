package pl.prodevcode.homekit.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Tlv8Test {

    @Test fun `round trips small items`() {
        val encoded = Tlv8().put(TlvType.STATE, 2).put(TlvType.IDENTIFIER, "abc").encode()
        val decoded = Tlv8.decode(encoded)
        assertEquals(2, decoded.byte(TlvType.STATE))
        assertEquals("abc", String(decoded[TlvType.IDENTIFIER]!!))
        assertNull(decoded[TlvType.ERROR])
    }

    @Test fun `fragments values longer than 255 bytes and merges them back`() {
        val big = ByteArray(600) { it.toByte() }
        val encoded = Tlv8().put(TlvType.PUBLIC_KEY, big).put(TlvType.STATE, 1).encode()
        // 3 fragments: 255 + 255 + 90, each with a 2-byte header, plus the state item
        assertEquals(600 + 3 * 2 + 3, encoded.size)
        val decoded = Tlv8.decode(encoded)
        assertArrayEquals(big, decoded[TlvType.PUBLIC_KEY])
        assertEquals(1, decoded.byte(TlvType.STATE))
    }

    @Test fun `separator keeps equal types apart`() {
        val encoded = Tlv8().put(TlvType.IDENTIFIER, "a").encode() +
            Tlv8().put(TlvType.SEPARATOR, ByteArray(0)).encode() +
            Tlv8().put(TlvType.IDENTIFIER, "b").encode()
        // the map keeps the last one; what matters is that "a" and "b" were not merged into "ab"
        assertEquals("b", String(Tlv8.decode(encoded)[TlvType.IDENTIFIER]!!))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects truncated input`() {
        Tlv8.decode(byteArrayOf(0x01, 0x05, 0x01))
    }
}
