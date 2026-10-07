package pl.prodevcode.homekit.pairing

import android.content.SharedPreferences
import androidx.core.content.edit
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import pl.prodevcode.homekit.crypto.HapCrypto

/** A controller (iPhone, Mac, home hub) that completed pair-setup or was added by an admin. */
data class Pairing(val identifier: String, val publicKey: ByteArray, val admin: Boolean) {
    override fun equals(other: Any?) = other is Pairing && other.identifier == identifier
    override fun hashCode() = identifier.hashCode()
}

/**
 * Everything the accessory must remember across restarts: its long-term Ed25519 identity,
 * the paired controllers and the HAP configuration number. Backed by SharedPreferences.
 */
class PairingStore(private val prefs: SharedPreferences) {

    /** Accessory identifier advertised as `id` in the `_hap._tcp` TXT record, `XX:XX:XX:XX:XX:XX`. */
    val accessoryId: String = prefs.getString(KEY_ID, null) ?: randomId().also { prefs.edit { putString(KEY_ID, it) } }

    /** 4-character setup ID that ties the QR code to this accessory (`sh` TXT record). */
    val setupId: String = prefs.getString(KEY_SETUP_ID, null) ?: randomSetupId().also { prefs.edit { putString(KEY_SETUP_ID, it) } }

    val keyPair: HapCrypto.Ed25519KeyPair = load()

    private val _pairings = MutableStateFlow(loadPairings())
    val pairings = _pairings.asStateFlow()

    val isPaired: Boolean get() = _pairings.value.isNotEmpty()

    /** `c#` TXT value; must change whenever the accessory database changes. */
    var configNumber: Int
        get() = prefs.getInt(KEY_CONFIG, 1)
        private set(value) = prefs.edit { putInt(KEY_CONFIG, value) }

    fun bumpConfigNumber() { configNumber = (configNumber % 65535) + 1 }

    /** Shape of the accessory database last advertised; lets the host bump `c#` when it changed across restarts. */
    var databaseSignature: String?
        get() = prefs.getString(KEY_DB_SIGNATURE, null)
        set(value) = prefs.edit { putString(KEY_DB_SIGNATURE, value) }

    fun find(identifier: String): Pairing? = _pairings.value.firstOrNull { it.identifier == identifier }

    @Synchronized
    fun add(pairing: Pairing) = save(_pairings.value.filterNot { it.identifier == pairing.identifier } + pairing)

    @Synchronized
    fun remove(identifier: String) = save(_pairings.value.filterNot { it.identifier == identifier })

    @Synchronized
    fun removeAll() = save(emptyList())

    private fun save(list: List<Pairing>) {
        _pairings.value = list
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.identifier).put("pk", b64(it.publicKey)).put("admin", it.admin))
        }
        prefs.edit { putString(KEY_PAIRINGS, arr.toString()) }
    }

    private fun loadPairings(): List<Pairing> {
        val raw = prefs.getString(KEY_PAIRINGS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Pairing(o.getString("id"), unb64(o.getString("pk")), o.optBoolean("admin", true))
            }
        }.getOrDefault(emptyList())
    }

    private fun load(): HapCrypto.Ed25519KeyPair {
        val priv = prefs.getString(KEY_PRIVATE, null)
        val pub = prefs.getString(KEY_PUBLIC, null)
        if (priv != null && pub != null) return HapCrypto.Ed25519KeyPair(unb64(priv), unb64(pub))
        val fresh = HapCrypto.Ed25519KeyPair.generate()
        prefs.edit { putString(KEY_PRIVATE, b64(fresh.privateKey)); putString(KEY_PUBLIC, b64(fresh.publicKey)) }
        return fresh
    }

    private fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String): ByteArray = Base64.getDecoder().decode(s)

    private companion object {
        const val KEY_ID = "accessory_id"
        const val KEY_PRIVATE = "ltsk"
        const val KEY_PUBLIC = "ltpk"
        const val KEY_PAIRINGS = "pairings"
        const val KEY_CONFIG = "config_number"
        const val KEY_SETUP_ID = "setup_id"
        const val KEY_DB_SIGNATURE = "db_signature"
        const val SETUP_ID_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"

        fun randomSetupId(): String {
            val rnd = SecureRandom()
            return String(CharArray(4) { SETUP_ID_ALPHABET[rnd.nextInt(SETUP_ID_ALPHABET.length)] })
        }

        fun randomId(): String {
            val b = ByteArray(6).also { SecureRandom().nextBytes(it) }
            return b.joinToString(":") { "%02X".format(it) }
        }
    }
}
