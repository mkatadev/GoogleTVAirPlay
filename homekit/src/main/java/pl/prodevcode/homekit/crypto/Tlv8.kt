package pl.prodevcode.homekit.crypto

import java.io.ByteArrayOutputStream

/** HAP TLV8 item types used by pair-setup, pair-verify and the pairings endpoint. */
object TlvType {
    const val METHOD = 0x00
    const val IDENTIFIER = 0x01
    const val SALT = 0x02
    const val PUBLIC_KEY = 0x03
    const val PROOF = 0x04
    const val ENCRYPTED_DATA = 0x05
    const val STATE = 0x06
    const val ERROR = 0x07
    const val RETRY_DELAY = 0x08
    const val CERTIFICATE = 0x09
    const val SIGNATURE = 0x0A
    const val PERMISSIONS = 0x0B
    const val FRAGMENT_DATA = 0x0C
    const val FRAGMENT_LAST = 0x0D
    const val FLAGS = 0x13
    const val SEPARATOR = 0xFF
}

object TlvError {
    const val UNKNOWN = 0x01
    const val AUTHENTICATION = 0x02
    const val BACKOFF = 0x03
    const val MAX_PEERS = 0x04
    const val MAX_TRIES = 0x05
    const val UNAVAILABLE = 0x06
    const val BUSY = 0x07
}

object TlvMethod {
    const val PAIR_SETUP = 0x00
    const val PAIR_SETUP_WITH_AUTH = 0x01
    const val PAIR_VERIFY = 0x02
    const val ADD_PAIRING = 0x03
    const val REMOVE_PAIRING = 0x04
    const val LIST_PAIRINGS = 0x05
}

/**
 * Type-length-value encoding with 1-byte type and length; values longer than 255 bytes are
 * split into consecutive fragments of the same type and merged again on decode.
 */
class Tlv8 private constructor(private val items: LinkedHashMap<Int, ByteArray>) {

    constructor() : this(LinkedHashMap())

    operator fun get(type: Int): ByteArray? = items[type]

    fun has(type: Int) = items.containsKey(type)

    fun byte(type: Int): Int? = items[type]?.takeIf { it.size == 1 }?.get(0)?.toInt()?.and(0xFF)

    fun put(type: Int, value: ByteArray) = apply { items[type] = value }

    fun put(type: Int, value: Int) = put(type, byteArrayOf(value.toByte()))

    fun put(type: Int, value: String) = put(type, value.toByteArray(Charsets.UTF_8))

    fun encode(): ByteArray {
        val out = ByteArrayOutputStream()
        for ((type, value) in items) {
            if (value.isEmpty()) {
                out.write(type); out.write(0)
                continue
            }
            var offset = 0
            while (offset < value.size) {
                val len = minOf(255, value.size - offset)
                out.write(type); out.write(len)
                out.write(value, offset, len)
                offset += len
            }
        }
        return out.toByteArray()
    }

    companion object {
        fun decode(data: ByteArray): Tlv8 {
            val items = LinkedHashMap<Int, ByteArray>()
            var i = 0
            var lastType = -1
            while (i + 2 <= data.size) {
                val type = data[i].toInt() and 0xFF
                val len = data[i + 1].toInt() and 0xFF
                i += 2
                if (i + len > data.size) throw IllegalArgumentException("truncated tlv item $type")
                val value = data.copyOfRange(i, i + len)
                i += len
                // a fragment continues the previous item only if it immediately follows one of the same type
                items[type] = if (type == lastType && items[type] != null) items[type]!! + value else value
                lastType = type
            }
            return Tlv8(items)
        }

        fun error(state: Int, code: Int): ByteArray =
            Tlv8().put(TlvType.STATE, state).put(TlvType.ERROR, code).encode()
    }
}
