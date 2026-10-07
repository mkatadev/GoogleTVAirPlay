package pl.prodevcode.homekit

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log

/** Advertises `_hap._tcp` (HAP 6.4) and re-registers whenever the TXT record must change. */
internal class HapAdvertiser(private val context: Context) {

    data class Record(
        val name: String,
        val port: Int,
        val accessoryId: String,
        val model: String,
        val configNumber: Int,
        val paired: Boolean,
        val category: Int,
        val setupHash: String,
    )

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var multicastLock: WifiManager.MulticastLock? = null
    private var listener: NsdManager.RegistrationListener? = null
    private var current: Record? = null

    fun advertise(record: Record) {
        if (record == current && listener != null) return
        current = record
        unregister()
        if (multicastLock == null) {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("homekit_mdns").apply { setReferenceCounted(false); acquire() }
        }
        val info = NsdServiceInfo().apply {
            serviceName = record.name
            serviceType = SERVICE_TYPE
            port = record.port
            setAttribute("c#", record.configNumber.toString())
            setAttribute("ff", "0")
            setAttribute("id", record.accessoryId)
            setAttribute("md", record.model)
            setAttribute("pv", "1.1")
            setAttribute("s#", "1")
            setAttribute("sf", if (record.paired) "0" else "1")
            setAttribute("ci", record.category.toString())
            setAttribute("sh", record.setupHash)
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) { Log.i(TAG, "registered ${i.serviceName} (sf=${if (record.paired) 0 else 1})") }
            override fun onRegistrationFailed(i: NsdServiceInfo, code: Int) { Log.e(TAG, "registration failed for ${i.serviceName}: $code") }
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, code: Int) {}
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
        listener = l
    }

    /** Same record again, e.g. after the network changed. */
    fun reannounce() {
        val record = current ?: return
        current = null
        advertise(record)
    }

    fun stop() {
        unregister()
        current = null
        multicastLock?.release()
        multicastLock = null
    }

    private fun unregister() {
        listener?.let { try { nsd.unregisterService(it) } catch (_: Exception) {} }
        listener = null
    }

    private companion object {
        const val TAG = "HapAdvertiser"
        const val SERVICE_TYPE = "_hap._tcp"
    }
}
