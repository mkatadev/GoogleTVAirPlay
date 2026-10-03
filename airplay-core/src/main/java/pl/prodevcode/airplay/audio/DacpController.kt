package pl.prodevcode.airplay.audio

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.Executors

/**
 * Sends DACP (Digital Audio Control Protocol) commands back to the AirPlay sender.
 * Resolves the sender's control port via mDNS, then sends HTTP GET requests.
 */
class DacpController(ctx: Context) {

    private val nsdManager = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val exec = Executors.newSingleThreadExecutor()

    @Volatile var dacpId = ""
    @Volatile var activeRemote = ""
    @Volatile private var host = ""
    @Volatile private var port = 0

    fun update(dacpId: String, activeRemote: String) {
        this.dacpId = dacpId
        this.activeRemote = activeRemote
        _resolve()
    }

    fun play() = _send("/ctrl-int/1/play")
    fun pause() = _send("/ctrl-int/1/pause")
    fun nextItem() = _send("/ctrl-int/1/nextitem")
    fun prevItem() = _send("/ctrl-int/1/previtem")
    fun volumeUp() = _send("/ctrl-int/1/volumeup")
    fun volumeDown() = _send("/ctrl-int/1/volumedown")
    fun muteToggle() = _send("/ctrl-int/1/mutetoggle")
    fun beginFastForward() = _send("/ctrl-int/1/beginff")
    fun beginRewind() = _send("/ctrl-int/1/beginrew")
    fun playResume() = _send("/ctrl-int/1/playresume")

    fun reset() {
        dacpId = ""
        activeRemote = ""
        host = ""
        port = 0
    }

    fun release() {
        reset()
        exec.shutdownNow()
    }

    private fun _resolve() {
        if (dacpId.isEmpty()) return
        val serviceName = "iTunes_Ctrl_$dacpId"
        val info = NsdServiceInfo().apply {
            serviceType = "_dacp._tcp"
            this.serviceName = serviceName
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) _resolveWithCallback(info)
            else _resolveLegacy(info)
        } catch (e: Exception) {
            Log.w(TAG, "DACP resolve error", e)
        }
    }

    private fun _onResolved(si: NsdServiceInfo, addresses: List<InetAddress>) {
        val addr = addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull() ?: return
        host = addr.hostAddress ?: return
        port = si.port
        Log.i(TAG, "DACP resolved: $host:$port")
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun _resolveWithCallback(info: NsdServiceInfo) {
        nsdManager.registerServiceInfoCallback(info, exec, object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(code: Int) {
                Log.w(TAG, "DACP resolve failed: $code")
            }
            override fun onServiceUpdated(si: NsdServiceInfo) {
                _onResolved(si, si.hostAddresses)
                // one-shot resolve: the sender's control port does not move during a session
                runCatching { nsdManager.unregisterServiceInfoCallback(this) }
            }
            override fun onServiceLost() {}
            override fun onServiceInfoCallbackUnregistered() {}
        })
    }

    @Suppress("DEPRECATION") // resolveService is the only API below 34
    private fun _resolveLegacy(info: NsdServiceInfo) {
        nsdManager.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(si: NsdServiceInfo, code: Int) {
                Log.w(TAG, "DACP resolve failed: $code")
            }
            override fun onServiceResolved(si: NsdServiceInfo) {
                _onResolved(si, listOfNotNull(si.host))
            }
        })
    }

    private fun _send(path: String): ListenableFuture<Unit> {
        val result = SettableFuture.create<Unit>()
        if (host.isEmpty() || activeRemote.isEmpty()) {
            result.setException(IOException("dacp endpoint not resolved"))
            return result
        }
        try {
            exec.execute {
                try {
                    val url = "http://$host:$port$path"
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Active-Remote", activeRemote)
                    conn.setRequestProperty("Host", "$host:$port")
                    conn.connectTimeout = 2000
                    conn.readTimeout = 2000
                    val code = conn.responseCode
                    try { conn.inputStream.readBytes() } catch (_: Exception) {}
                    conn.disconnect()
                    if (code in 200..299) {
                        result.set(Unit)
                    } else {
                        Log.w(TAG, "DACP $path -> HTTP $code")
                        result.setException(IOException("HTTP $code"))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "DACP send failed: $path", e)
                    result.setException(e)
                }
            }
        } catch (e: Exception) {
            result.setException(e)
        }
        return result
    }

    companion object {
        private const val TAG = "DacpController"
    }
}
