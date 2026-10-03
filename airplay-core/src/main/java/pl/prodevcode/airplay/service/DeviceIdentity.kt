package pl.prodevcode.airplay.service

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import java.net.NetworkInterface
import java.security.SecureRandom
import pl.prodevcode.airplay.Prefs

/** Stable hardware address the receiver identifies itself with over AirPlay. */
internal class DeviceIdentity(private val prefs: SharedPreferences) {

    fun hardwareAddress(): ByteArray {
        try {
            for (iface in NetworkInterface.getNetworkInterfaces()) {
                if (iface.name.startsWith("wlan") || iface.name.startsWith("eth")) {
                    val mac = iface.hardwareAddress
                    if (isUsable(mac)) return mac
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get hardware address", e)
        }
        // fall back to stable per-install random address
        return persistedRandomMac()
            ?: byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte())
    }

    private fun isUsable(mac: ByteArray?): Boolean =
        mac != null && mac.size == 6 &&
            mac.any { it != 0.toByte() } &&
            !(mac[0] == 0x02.toByte() && mac.drop(1).all { it == 0.toByte() })

    private fun persistedRandomMac(): ByteArray? {
        fromString(prefs.getString(Prefs.FALLBACK_MAC_ADDRESS, null))
            ?.takeIf { isUsable(it) }?.let { return it }
        repeat(10) {
            val mac = randomAaiMac()
            if (isUsable(mac)) {
                prefs.edit { putString(Prefs.FALLBACK_MAC_ADDRESS, toString(mac)) }
                return mac
            }
        }
        return null
    }

    // random locally-administered unicast MAC in AAI SLAP quadrant
    private fun randomAaiMac(): ByteArray {
        val mac = ByteArray(6).also { SecureRandom().nextBytes(it) }
        mac[0] = ((mac[0].toInt() and 0xF0) or 0x0A).toByte()
        return mac
    }

    private fun toString(mac: ByteArray): String = mac.joinToString(":") { "%02x".format(it) }

    private fun fromString(s: String?): ByteArray? {
        if (s == null) return null
        return try {
            s.split(":").map { it.toInt(16).toByte() }.toByteArray()
        } catch (_: Exception) { null }
    }

    private companion object { const val TAG = "DeviceIdentity" }
}
