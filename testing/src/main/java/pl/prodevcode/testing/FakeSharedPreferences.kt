package pl.prodevcode.testing

import android.content.SharedPreferences

/** In-memory SharedPreferences for JVM tests; listeners fire synchronously on commit/apply. */
class FakeSharedPreferences : SharedPreferences {
    private val values = HashMap<String, Any?>()
    private val listeners = LinkedHashSet<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = HashMap(values)
    override fun getString(key: String, defValue: String?) = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = values[key] as? MutableSet<String> ?: defValues
    override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String) = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners += l }
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners -= l }

    private inner class Editor : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private var clear = false
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { pending[key] = null }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) values.clear()
            pending.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
            val changed = pending.keys.toList()
            pending.clear(); clear = false
            changed.forEach { k -> listeners.toList().forEach { it.onSharedPreferenceChanged(this@FakeSharedPreferences, k) } }
        }
    }
}
