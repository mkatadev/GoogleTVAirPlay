package pl.prodevcode.homekit.server

import android.util.Log
import java.io.IOException
import java.net.Socket
import pl.prodevcode.homekit.pairing.PairSetup
import pl.prodevcode.homekit.pairing.PairVerify
import pl.prodevcode.homekit.pairing.SessionKeys

interface HapRequestHandler {
    fun handle(connection: HapConnection, request: HttpRequest): HttpResponse
    fun onClosed(connection: HapConnection)
}

/**
 * One controller connection: reads requests (plain until pair-verify completes, encrypted after),
 * dispatches them to [handler] and lets the accessory push `EVENT/1.0` messages at any time.
 */
class HapConnection(private val socket: Socket, private val handler: HapRequestHandler) : Runnable {

    var pairSetup: PairSetup? = null
    var pairVerify: PairVerify? = null

    /** Verified controller, once pair-verify completed. */
    var session: SessionKeys? = null
        private set
    private var channel: SecureChannel? = null
    private var pendingSession: SessionKeys? = null

    /** Characteristic instance ids this controller subscribed to. */
    val subscriptions = HashSet<Int>()

    private val writeLock = Any()
    @Volatile private var closed = false
    val remote: String = socket.inetAddress?.hostAddress ?: "?"

    /** Called by the handler when pair-verify M4 is about to be sent: encryption starts after that response. */
    fun activateSession(keys: SessionKeys) { pendingSession = keys }

    override fun run() {
        val parser = HttpRequestParser()
        val buf = ByteArray(4096)
        try {
            val input = socket.getInputStream()
            while (!closed) {
                val n = input.read(buf)
                if (n < 0) { Log.d(TAG, "$remote: closed by peer"); break }
                val secure = channel
                if (secure == null) {
                    parser.feed(buf, 0, n)
                } else {
                    val plain = secure.decrypt(buf.copyOf(n)) ?: run { Log.w(TAG, "$remote: frame authentication failed"); break }
                    parser.feed(plain)
                }
                while (true) {
                    val request = parser.next() ?: break
                    val response = handler.handle(this, request)
                    if (Log.isLoggable(TAG, Log.VERBOSE)) {
                        Log.v(TAG, "$remote: ${request.method} ${request.path}?${request.query} ${String(request.body)} -> ${response.status} ${String(response.body)}")
                    }
                    send(response.serialize())
                    pendingSession?.let { keys ->
                        session = keys
                        channel = SecureChannel(keys)
                        pendingSession = null
                    }
                }
            }
        } catch (e: IOException) {
            if (!closed) Log.d(TAG, "$remote: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "$remote: request failed", e)
        } finally {
            close()
            handler.onClosed(this)
        }
    }

    fun sendEvent(json: String) {
        if (channel == null) return
        if (Log.isLoggable(TAG, Log.VERBOSE)) Log.v(TAG, "$remote: EVENT $json")
        runCatching { send(HttpResponse.json(200, json).serialize("EVENT/1.0")) }
            .onFailure { Log.d(TAG, "$remote: event failed: ${it.message}") }
    }

    private fun send(bytes: ByteArray) {
        synchronized(writeLock) {
            val out = socket.getOutputStream()
            out.write(channel?.encrypt(bytes) ?: bytes)
            out.flush()
        }
    }

    fun close() {
        if (closed) return
        closed = true
        try { socket.close() } catch (_: IOException) {}
    }

    private companion object { const val TAG = "HapConnection" }
}
