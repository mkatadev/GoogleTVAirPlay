package pl.prodevcode.tvairplay.presentation.receiver

import android.view.Surface
import androidx.media3.common.text.Cue
import pl.prodevcode.tvairplay.domain.model.DeviceInfo
import pl.prodevcode.tvairplay.domain.model.ReceiverState
import pl.prodevcode.tvairplay.presentation.mvi.NoEffect
import pl.prodevcode.tvairplay.presentation.mvi.UiIntent
import pl.prodevcode.tvairplay.presentation.mvi.UiState

data class ReceiverUiState(
    val receiver: ReceiverState = ReceiverState(),
    val device: DeviceInfo = DeviceInfo(name = "", ipAddress = null),
    val keepScreenOn: Boolean = false,
    val runInBackground: Boolean = true,
    val overlayPermissionGranted: Boolean = true,
    /** Audio-only session idle long enough: show the dim overlay, let the TV sleep when paused. */
    val dimmed: Boolean = false,
    /** Newer release tag when one exists; shown as a hint on the idle screen. */
    val updateAvailable: String? = null,
    /** Live subtitle cues of the AirPlay video. */
    val cues: List<Cue> = emptyList(),
) : UiState

sealed interface ReceiverIntent : UiIntent {
    /** Screen became visible: make sure the receiver is running. */
    data object EnsureStarted : ReceiverIntent
    data object AppResumed : ReceiverIntent
    data object AppBackgrounded : ReceiverIntent
    data object ToggleReceiver : ReceiverIntent
    data object GrantOverlay : ReceiverIntent
    /** Any remote key: wakes a dimmed screen and restarts the idle timer. */
    data object UserInteraction : ReceiverIntent

    data object PlayPause : ReceiverIntent
    data class SeekBy(val deltaMs: Long) : ReceiverIntent
    data class SeekTo(val positionMs: Long) : ReceiverIntent
    data object Next : ReceiverIntent
    data object Previous : ReceiverIntent
    data object StopVideo : ReceiverIntent
    data class SelectAudioTrack(val id: String) : ReceiverIntent
    /** `null` = subtitles off. */
    data class SelectSubtitleTrack(val id: String?) : ReceiverIntent

    data class MirrorSurfaceReady(val surface: Surface) : ReceiverIntent
    data class MirrorSurfaceGone(val surface: Surface) : ReceiverIntent
    data class VideoSurfaceReady(val surface: Surface) : ReceiverIntent
    data class VideoSurfaceGone(val surface: Surface) : ReceiverIntent
}

typealias ReceiverEffect = NoEffect
