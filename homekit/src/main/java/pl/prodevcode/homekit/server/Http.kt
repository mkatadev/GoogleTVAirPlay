package pl.prodevcode.homekit.server

import java.io.ByteArrayOutputStream
import java.net.URLDecoder

class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: ByteArray,
)

class HttpResponse(val status: Int, val body: ByteArray = ByteArray(0), val contentType: String? = null) {

    /** `EVENT/1.0` messages reuse the HTTP framing but a different protocol token. */
    fun serialize(protocol: String = "HTTP/1.1"): ByteArray {
        val head = StringBuilder()
        head.append(protocol).append(' ').append(status).append(' ').append(reason(status)).append("\r\n")
        contentType?.let { head.append("Content-Type: ").append(it).append("\r\n") }
        head.append("Content-Length: ").append(body.size).append("\r\n\r\n")
        return head.toString().toByteArray(Charsets.ISO_8859_1) + body
    }

    companion object {
        const val JSON = "application/hap+json"
        const val TLV = "application/pairing+tlv8"

        fun json(status: Int, json: String) = HttpResponse(status, json.toByteArray(), JSON)
        fun tlv(body: ByteArray) = HttpResponse(200, body, TLV)
        fun noContent() = HttpResponse(204)
        fun badRequest() = json(400, """{"status":-70410}""")
        fun notFound() = HttpResponse(404)
        /** Secure endpoints hit over a plain connection. */
        fun unauthorized() = json(470, """{"status":-70401}""")

        private fun reason(status: Int) = when (status) {
            200 -> "OK"; 204 -> "No Content"; 207 -> "Multi-Status"; 400 -> "Bad Request"
            404 -> "Not Found"; 422 -> "Unprocessable Entity"; 470 -> "Connection Authorization Required"
            500 -> "Internal Server Error"; else -> "Unknown"
        }
    }
}

/**
 * Incremental HTTP/1.1 request parser: feed bytes as they arrive (plain or decrypted), take
 * complete requests out with [next]. Only `Content-Length` bodies, which is all HAP uses.
 */
class HttpRequestParser {
    private val buffer = ByteArrayOutputStream()

    fun feed(data: ByteArray, offset: Int = 0, length: Int = data.size - offset) = buffer.write(data, offset, length)

    fun next(): HttpRequest? {
        val bytes = buffer.toByteArray()
        val headerEnd = indexOfHeaderEnd(bytes)
        if (headerEnd < 0) return null
        val headerText = String(bytes, 0, headerEnd, Charsets.ISO_8859_1)
        val lines = headerText.split("\r\n")
        val requestLine = lines.first().split(' ')
        if (requestLine.size < 2) throw IllegalArgumentException("bad request line")
        val headers = HashMap<String, String>()
        lines.drop(1).forEach { line ->
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        val bodyStart = headerEnd + 4
        if (bytes.size < bodyStart + contentLength) return null
        val body = bytes.copyOfRange(bodyStart, bodyStart + contentLength)
        buffer.reset()
        buffer.write(bytes, bodyStart + contentLength, bytes.size - bodyStart - contentLength)

        val target = requestLine[1]
        val q = target.indexOf('?')
        val path = if (q >= 0) target.substring(0, q) else target
        val query = if (q >= 0) parseQuery(target.substring(q + 1)) else emptyMap()
        return HttpRequest(requestLine[0], path, query, headers, body)
    }

    private fun parseQuery(s: String): Map<String, String> = s.split('&').filter { it.isNotEmpty() }.associate { kv ->
        val i = kv.indexOf('=')
        val k = if (i >= 0) kv.substring(0, i) else kv
        val v = if (i >= 0) URLDecoder.decode(kv.substring(i + 1), "UTF-8") else ""
        k to v
    }

    private fun indexOfHeaderEnd(b: ByteArray): Int {
        for (i in 0..b.size - 4) {
            if (b[i] == '\r'.code.toByte() && b[i + 1] == '\n'.code.toByte() &&
                b[i + 2] == '\r'.code.toByte() && b[i + 3] == '\n'.code.toByte()
            ) return i
        }
        return -1
    }
}
