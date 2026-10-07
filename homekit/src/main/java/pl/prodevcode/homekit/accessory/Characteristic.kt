package pl.prodevcode.homekit.accessory

import org.json.JSONArray
import org.json.JSONObject

enum class Format(val hap: String) {
    BOOL("bool"), UINT8("uint8"), UINT16("uint16"), UINT32("uint32"), INT("int"), FLOAT("float"), STRING("string"), TLV8("tlv8")
}

enum class Perm(val hap: String) { READ("pr"), WRITE("pw"), EVENTS("ev") }

/** HAP status codes returned per characteristic in 207 responses. */
object HapStatus {
    const val SUCCESS = 0
    const val INSUFFICIENT_PRIVILEGES = -70401
    const val READ_ONLY = -70404
    const val WRITE_ONLY = -70405
    const val NOTIFICATION_UNSUPPORTED = -70406
    const val RESOURCE_MISSING = -70409
    const val INVALID_VALUE = -70410
}

/**
 * One characteristic of a service. [value] is the last known value; [onWrite] is invoked when a
 * controller writes and [onChanged] when the accessory updates the value itself (feeds events).
 */
class Characteristic(
    val type: String,
    val format: Format,
    val perms: Set<Perm>,
    value: Any?,
    private val minValue: Number? = null,
    private val maxValue: Number? = null,
    private val minStep: Number? = null,
    validValues: List<Int>? = null,
    private val onWrite: ((Any) -> Unit)? = null,
    iid: Int = 0,
) {
    var iid = iid
        internal set

    /** Allowed values for enumerations; may change when the accessory database is rebuilt (e.g. input list). */
    var validValues: List<Int>? = validValues
        internal set
    var aid = 1
        internal set

    @Volatile var value: Any? = value?.let(::coerce)
        private set

    internal var onChanged: ((Characteristic) -> Unit)? = null

    val readable get() = Perm.READ in perms
    val writable get() = Perm.WRITE in perms
    val notifies get() = Perm.EVENTS in perms

    /** Accessory-side update: records the value and notifies subscribers if it changed. */
    fun update(newValue: Any?) {
        val coerced = coerce(newValue) ?: return
        if (coerced == value) return
        value = coerced
        onChanged?.invoke(this)
    }

    /** Controller write: validates, applies and tells the owner. Returns a HAP status. */
    fun write(raw: Any?): Int {
        if (!writable) return HapStatus.READ_ONLY
        val coerced = coerce(raw) ?: return HapStatus.INVALID_VALUE
        if (!inRange(coerced)) return HapStatus.INVALID_VALUE
        if (readable) value = coerced
        onWrite?.invoke(coerced)
        return HapStatus.SUCCESS
    }

    private fun inRange(v: Any): Boolean {
        val n = v as? Number ?: return true
        val d = n.toDouble()
        if (minValue != null && d < minValue.toDouble()) return false
        if (maxValue != null && d > maxValue.toDouble()) return false
        val allowed = validValues
        if (allowed != null && n.toInt() !in allowed) return false
        return true
    }

    private fun coerce(raw: Any?): Any? = when (format) {
        Format.BOOL -> when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> raw.equals("true", ignoreCase = true) || raw == "1"
            else -> null
        }
        Format.UINT8, Format.UINT16, Format.UINT32, Format.INT -> when (raw) {
            is Number -> raw.toLong()
            is Boolean -> if (raw) 1L else 0L
            is String -> raw.toLongOrNull()
            else -> null
        }
        Format.FLOAT -> (raw as? Number)?.toDouble() ?: (raw as? String)?.toDoubleOrNull()
        Format.STRING, Format.TLV8 -> raw?.toString()
    }

    fun toJson(withMeta: Boolean): JSONObject = JSONObject().apply {
        put("iid", iid)
        put("type", type)
        put("perms", JSONArray(perms.map { it.hap }))
        put("format", format.hap)
        if (readable) put("value", jsonValue())
        if (withMeta) {
            minValue?.let { put("minValue", it) }
            maxValue?.let { put("maxValue", it) }
            minStep?.let { put("minStep", it) }
            validValues?.let { put("valid-values", JSONArray(it)) }
        }
    }

    /** `null` encodes as JSON null, which HAP uses for write-only characteristics. */
    fun jsonValue(): Any = value ?: JSONObject.NULL
}
