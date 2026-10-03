package pl.prodevcode.tvairplay.presentation.receiver

import android.view.Surface
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
) : UiState

sealed interface ReceiverIntent : UiIntent {
    /** Screen became visible: make sure the receiver is running. */
    data object EnsureStarted : ReceiverIntent
    data object AppResumed : ReceiverIntent
    data object AppBackgrounded : ReceiverIntent
    data object ToggleReceiver : ReceiverIntent
    data object GrantOverlay : ReceiverIntent

    data object PlayPause : ReceiverIntent
    data class SeekBy(val deltaMs: Long) : ReceiverIntent
    data class SeekTo(val positionMs: Long) : ReceiverIntent
    data object Next : ReceiverIntent
    data object Previous : ReceiverIntent
    data object StopVideo : ReceiverIntent

    data class MirrorSurfaceReady(val surface: Surface) : ReceiverIntent
    data class MirrorSurfaceGone(val surface: Surface) : ReceiverIntent
    data class VideoSurfaceReady(val surface: Surface) : ReceiverIntent
    data class VideoSurfaceGone(val surface: Surface) : ReceiverIntent
}

typealias ReceiverEffect = NoEffect
