package pl.prodevcode.tvairplay.domain.model

/** Mirroring/audio trade-off between delay and smoothness. */
enum class LatencyMode { LOW, BALANCED, SMOOTH }

/** How much of the sender's ~2 s lead music keeps buffered; AIRPLAY = exactly on the sender's schedule. */
enum class MusicDelay { SHORT, MEDIUM, AIRPLAY }

val IDLE_DIM_OPTIONS = listOf(0, 1, 5, 15)

/** UI language; SYSTEM follows the TV. */
enum class AppLanguage(val tag: String) {
    SYSTEM(""), ENGLISH("en"), POLISH("pl");

    companion object {
        fun fromTag(tag: String?) = entries.firstOrNull { it.tag == (tag ?: "") } ?: SYSTEM
    }
}

data class ReceiverSettings(
    val deviceName: String = "Google TV",
    val startOnBoot: Boolean = true,
    val runInBackground: Boolean = true,
    val openAppOnConnect: Boolean = true,
    val requirePin: Boolean = true,
    /** Senders that entered the PIN once skip it next time. */
    val rememberDevices: Boolean = true,
    val hevcEnabled: Boolean = true,
    val advertiseVideo: Boolean = true,
    val advertiseAudio: Boolean = true,
    val latencyMode: LatencyMode = LatencyMode.BALANCED,
    val musicDelay: MusicDelay = MusicDelay.AIRPLAY,
    /** Minutes of audio-only idling before the screen dims; 0 = never. */
    val idleDimMinutes: Int = 5,
    /** Show HLS subtitles in the system language without asking. */
    val subtitlesByDefault: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM,
    /** Show up in Apple Home as a TV (power, remote keys, volume). */
    val homeKitEnabled: Boolean = false,
)
