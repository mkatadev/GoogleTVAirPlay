package pl.prodevcode.homekit.server

import android.util.Log
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.concurrent.thread

/** Accepts controller connections on an ephemeral TCP port; one thread per connection. */
class HapServer(private val handler: HapRequestHandler) {

    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private val connections = CopyOnWriteArraySet<HapConnection>()

    val port: Int get() = serverSocket?.localPort ?: 0

    @Synchronized
    fun start(): Int {
        serverSocket?.let { return it.localPort }
        val socket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(0))
        }
        serverSocket = socket
        acceptThread = thread(name = "hap-accept", isDaemon = true) {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (_: IOException) { break }
                client.tcpNoDelay = true
                val conn = HapConnection(client, wrapped)
                connections += conn
                thread(name = "hap-conn-${conn.remote}", isDaemon = true) { conn.run() }
            }
        }
        return socket.localPort
    }

    private val wrapped = object : HapRequestHandler {
        override fun handle(connection: HapConnection, request: HttpRequest) = handler.handle(connection, request)
        override fun onClosed(connection: HapConnection) {
            connections -= connection
            handler.onClosed(connection)
        }
    }

    fun connections(): Set<HapConnection> = connections

    @Synchronized
    fun stop() {
        try { serverSocket?.close() } catch (_: IOException) {}
        serverSocket = null
        connections.forEach { it.close() }
        connections.clear()
        acceptThread?.join(1_000)
        acceptThread = null
        Log.d(TAG, "stopped")
    }

    private companion object { const val TAG = "HapServer" }
}
