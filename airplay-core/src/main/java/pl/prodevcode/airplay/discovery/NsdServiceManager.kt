package pl.prodevcode.airplay.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class NsdServiceManager(private val ctx: Context) {

    enum class State { IDLE, PENDING, REGISTERED, FAILED }

    data class Status(
        val raop: State = State.IDLE,
        val airplay: State = State.IDLE,
        val raopError: Int = 0,
        val airplayError: Int = 0,
        val registrations: Int = 0,
    )

    private data class Params(val name: String, val port: Int, val txt: Map<String, String>)

    private val nsdManager = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var multicastLock: WifiManager.MulticastLock? = null
    private var raopRegistration: NsdManager.RegistrationListener? = null
    private var airplayRegistration: NsdManager.RegistrationListener? = null
    private var raopParams: Params? = null
    private var airplayParams: Params? = null

    private val _status = MutableStateFlow(Status())
    val status = _status.asStateFlow()

    fun acquireMulticastLock() {
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("airplay_mdns").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    fun registerRaop(serviceName: String, port: Int, txtRecords: Map<String, String>) {
        raopParams = Params(serviceName, port, txtRecords)
        raopRegistration?.let { unregister(it) }
        _status.value = _status.value.copy(raop = State.PENDING, raopError = 0)
        raopRegistration = register("_raop._tcp", serviceName, port, txtRecords,
            onState = { s, code -> _status.value = _status.value.copy(raop = s, raopError = code) })
    }

    fun registerAirplay(serviceName: String, port: Int, txtRecords: Map<String, String>) {
        airplayParams = Params(serviceName, port, txtRecords)
        airplayRegistration?.let { unregister(it) }
        _status.value = _status.value.copy(airplay = State.PENDING, airplayError = 0)
        airplayRegistration = register("_airplay._tcp", serviceName, port, txtRecords,
            onState = { s, code -> _status.value = _status.value.copy(airplay = s, airplayError = code) })
    }

    /** Re-announces both services with the last used parameters, e.g. after a network change. */
    fun reregister() {
        _status.value = _status.value.copy(registrations = _status.value.registrations + 1)
        raopParams?.let { registerRaop(it.name, it.port, it.txt) }
        airplayParams?.let { registerAirplay(it.name, it.port, it.txt) }
    }

    private fun register(
        type: String,
        serviceName: String,
        port: Int,
        txtRecords: Map<String, String>,
        onState: (State, Int) -> Unit,
    ): NsdManager.RegistrationListener {
        val info = NsdServiceInfo().apply {
            this.serviceName = serviceName
            serviceType = type
            this.port = port
            txtRecords.forEach { (k, v) -> setAttribute(k, v) }
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "$type registered: ${info.serviceName}")
                onState(State.REGISTERED, 0)
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {
                Log.e(TAG, "$type registration failed: $code")
                onState(State.FAILED, code)
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.i(TAG, "$type unregistered")
            }
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {
                Log.e(TAG, "$type unregister failed: $code")
            }
        }
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        return listener
    }

    private fun unregister(listener: NsdManager.RegistrationListener) {
        try { nsdManager.unregisterService(listener) } catch (_: Exception) {}
    }

    fun unregisterAll() {
        raopRegistration?.let { unregister(it); raopRegistration = null }
        airplayRegistration?.let { unregister(it); airplayRegistration = null }
        _status.value = _status.value.copy(raop = State.IDLE, airplay = State.IDLE)
    }

    fun release() {
        unregisterAll()
        multicastLock?.release()
        multicastLock = null
    }

    companion object {
        private const val TAG = "NsdServiceManager"
    }
}
