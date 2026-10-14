package pl.prodevcode.airplay.service

/**
 * How much of the sender's ~2 s lead an audio-only session keeps buffered before playing.
 * [capMs] = 0 plays exactly on the sender's schedule (every AirPlay speaker does); a cap
 * starts sooner but leaves less time for Wi-Fi bursts and resends.
 */
enum class MusicDelay(val pref: String, val capMs: Int) {
    SHORT("short", 500), MEDIUM("medium", 1000), AIRPLAY("airplay", 0);

    companion object {
        fun fromPref(value: String?) = entries.firstOrNull { it.pref == value } ?: AIRPLAY
    }
}
