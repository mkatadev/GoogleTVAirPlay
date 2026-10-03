package pl.prodevcode.tvairplay.platform

/** Android-specific: the per-app locale override (AppCompat / Android 13 per-app languages). */
interface AppLocale {
    /** BCP-47 tag of the current override, "" when following the system. */
    fun current(): String
    /** Applies the override and recreates visible activities; "" clears it. */
    fun apply(tag: String)
}
