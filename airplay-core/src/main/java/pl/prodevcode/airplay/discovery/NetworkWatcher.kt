package pl.prodevcode.airplay.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NetworkStatus(
    val transport: String = "",
    val interfaceName: String = "",
    val addresses: List<String> = emptyList(),
    /** How many times [NetworkWatcher] reported a settled address change. */
    val changes: Int = 0,
)

/**
 * Tracks the IPv4 addresses of Wi-Fi/Ethernet networks and fires [onChanged] once they settle
 * after a change, so the receiver can re-announce itself without a manual restart.
 */
class NetworkWatcher(
    context: Context,
    private val onChanged: (addresses: Set<String>) -> Unit,
) {
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val handler = Handler(Looper.getMainLooper())
    private val addrsByNetwork = HashMap<Network, Set<String>>()
    private var lastSettled: Set<String> = emptySet()
    private var callback: ConnectivityManager.NetworkCallback? = null
    private val settle = Runnable { _fire() }

    private val _status = MutableStateFlow(NetworkStatus())
    val status = _status.asStateFlow()

    fun start() {
        if (callback != null) return
        addrsByNetwork.clear()
        lastSettled = emptySet()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                val addrs = lp.linkAddresses.mapNotNull { it.address as? Inet4Address }
                    .filter { !it.isLoopbackAddress }
                    .mapNotNull { it.hostAddress }.toSet()
                handler.post { addrsByNetwork[network] = addrs; _onChanged(network, lp) }
            }
            override fun onLost(network: Network) {
                handler.post { addrsByNetwork.remove(network); _onChanged(null, null) }
            }
        }
        try {
            connectivity.registerNetworkCallback(request, cb)
            callback = cb
        } catch (_: Exception) {
            // no connectivity permission / service: run without re-announce
        }
    }

    fun stop() {
        callback?.let { try { connectivity.unregisterNetworkCallback(it) } catch (_: Exception) {} }
        callback = null
        handler.removeCallbacks(settle)
        addrsByNetwork.clear()
        lastSettled = emptySet()
        _status.value = NetworkStatus()
    }

    private fun _onChanged(network: Network?, lp: LinkProperties?) {
        val all = addrsByNetwork.values.flatten().toSet()
        val active = network ?: connectivity.activeNetwork
        val caps = active?.let { connectivity.getNetworkCapabilities(it) }
        val transport = when {
            caps == null -> ""
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            else -> "other"
        }
        val iface = (lp ?: active?.let { connectivity.getLinkProperties(it) })?.interfaceName ?: ""
        _status.value = _status.value.copy(transport = transport, interfaceName = iface, addresses = all.sorted())

        // first callback after start only seeds the baseline; the caller just announced itself
        if (lastSettled.isEmpty() && all.isNotEmpty()) { lastSettled = all; return }
        if (all == lastSettled) return
        handler.removeCallbacks(settle)
        if (all.isEmpty()) {
            // offline: nothing to announce yet, wait for the next address
            lastSettled = emptySet()
            return
        }
        // dhcp usually settles within a second; coalesce the burst of callbacks into one re-announce
        handler.postDelayed(settle, SETTLE_MS)
    }

    private fun _fire() {
        val all = addrsByNetwork.values.flatten().toSet()
        if (all.isEmpty()) return
        lastSettled = all
        _status.value = _status.value.copy(changes = _status.value.changes + 1)
        onChanged(all)
    }

    private companion object { const val SETTLE_MS = 1500L }
}
