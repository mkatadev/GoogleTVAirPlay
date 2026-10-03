package pl.prodevcode.airplay.security

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import pl.prodevcode.airplay.Prefs

data class TrustedDevice(
    val deviceId: String,
    val publicKey: String,
    val name: String,
    val addedAt: Long,
    val lastSeenAt: Long,
)

/**
 * Senders that completed the PIN pairing. Keyed by the sender's Ed25519 public key, which is
 * what pair-verify presents; a trusted key skips the PIN prompt until it is forgotten here.
 */
class TrustedDeviceStore(private val prefs: SharedPreferences, private val now: () -> Long = System::currentTimeMillis) {

    private val _devices = MutableStateFlow(load())
    val devices = _devices.asStateFlow()

    /** Senders that paired during this run while remembering was off — trusted until the receiver restarts. */
    private val sessionKeys = HashSet<String>()

    private val remember: Boolean get() = prefs.getBoolean(Prefs.REMEMBER_DEVICES, Prefs.DEF_REMEMBER_DEVICES)

    @Synchronized
    fun isTrusted(publicKey: String): Boolean {
        if (publicKey in sessionKeys) return true
        if (!remember) return false
        val known = _devices.value.firstOrNull { it.publicKey == publicKey } ?: return false
        save(_devices.value.map { if (it === known) it.copy(lastSeenAt = now()) else it })
        return true
    }

    @Synchronized
    fun register(deviceId: String, publicKey: String, name: String) {
        if (!remember) { sessionKeys += publicKey; return }
        val t = now()
        val existing = _devices.value.firstOrNull { it.publicKey == publicKey }
        val updated = if (existing == null) {
            _devices.value + TrustedDevice(deviceId, publicKey, name.ifBlank { deviceId }, addedAt = t, lastSeenAt = t)
        } else {
            _devices.value.map { if (it === existing) it.copy(deviceId = deviceId, name = name.ifBlank { it.name }, lastSeenAt = t) else it }
        }
        save(updated)
    }

    @Synchronized
    fun forget(publicKey: String) {
        sessionKeys -= publicKey
        save(_devices.value.filterNot { it.publicKey == publicKey })
    }

    @Synchronized
    fun forgetAll() {
        sessionKeys.clear()
        save(emptyList())
    }

    private fun save(list: List<TrustedDevice>) {
        _devices.value = list
        val arr = JSONArray()
        list.forEach { d ->
            arr.put(JSONObject().apply {
                put("id", d.deviceId); put("pk", d.publicKey); put("name", d.name)
                put("added", d.addedAt); put("seen", d.lastSeenAt)
            })
        }
        prefs.edit { putString(Prefs.TRUSTED_DEVICES, arr.toString()) }
    }

    private fun load(): List<TrustedDevice> {
        val raw = prefs.getString(Prefs.TRUSTED_DEVICES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TrustedDevice(
                    deviceId = o.optString("id"), publicKey = o.getString("pk"), name = o.optString("name"),
                    addedAt = o.optLong("added"), lastSeenAt = o.optLong("seen"),
                )
            }
        }.getOrDefault(emptyList())
    }
}
