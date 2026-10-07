package pl.prodevcode.tvairplay.presentation.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.prodevcode.tvairplay.R

/**
 * The service logs in English (logcat, diagnostics); the few lines a viewer actually sees on the
 * idle screen are mapped to localized strings here. Unknown lines pass through unchanged.
 */
object LogMessages {

    class Localized(@param:StringRes val res: Int, val args: List<Any>)

    private val patterns: List<Pair<Regex, (MatchResult) -> Localized>> = listOf(
        Regex("""^Server started on port (\d+)$""") to { m -> Localized(R.string.log_server_started, listOf(m.groupValues[1].toInt())) },
        Regex("""^Server stopped$""") to { Localized(R.string.log_server_stopped, emptyList()) },
        Regex("""^Client connected \((\d+)\)$""") to { m -> Localized(R.string.log_client_connected, listOf(m.groupValues[1].toInt())) },
        Regex("""^Client disconnected \((\d+)\)$""") to { m -> Localized(R.string.log_client_disconnected, listOf(m.groupValues[1].toInt())) },
        Regex("""^Client reconnected \((\d+)\)$""") to { m -> Localized(R.string.log_client_reconnected, listOf(m.groupValues[1].toInt())) },
        Regex("""^Paired: (.+)$""") to { m -> Localized(R.string.log_paired, listOf(m.groupValues[1])) },
        Regex("""^Audio mode$""") to { Localized(R.string.log_audio_mode, emptyList()) },
        Regex("""^Mirror mode$""") to { Localized(R.string.log_mirror_mode, emptyList()) },
        Regex("""^Last client gone, holding session (\d+)s$""") to { m -> Localized(R.string.log_last_client_gone, listOf(m.groupValues[1].toInt())) },
        Regex("""^Screen off, suspending receiver$""") to { Localized(R.string.log_screen_off, emptyList()) },
        Regex("""^Screen on, resuming receiver$""") to { Localized(R.string.log_screen_on, emptyList()) },
        Regex("""^Network changed .*re-announcing AirPlay$""") to { Localized(R.string.log_network_changed, emptyList()) },
        Regex("""^AirPlay Video play: .*$""") to { Localized(R.string.log_video_play, emptyList()) },
        Regex("""^AirPlay Video stopped.*$""") to { Localized(R.string.log_video_stopped, emptyList()) },
        Regex("""^Track: (.+)$""") to { m -> Localized(R.string.log_track, listOf(m.groupValues[1])) },
    )

    fun match(raw: String): Localized? {
        val line = raw.trim()
        for ((regex, build) in patterns) regex.matchEntire(line)?.let { return build(it) }
        return null
    }
}

/** [raw] service log line in the UI language when it is one of the known events, otherwise as is. */
@Composable
fun localizedLog(raw: String): String {
    val m = LogMessages.match(raw) ?: return raw
    return stringResource(m.res, *m.args.toTypedArray())
}
