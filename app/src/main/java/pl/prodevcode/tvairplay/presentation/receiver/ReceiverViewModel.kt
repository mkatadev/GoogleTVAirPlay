package pl.prodevcode.tvairplay.presentation.receiver

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import pl.prodevcode.tvairplay.domain.model.SessionMode
import pl.prodevcode.tvairplay.domain.repository.ReceiverRepository
import pl.prodevcode.tvairplay.domain.usecase.ObserveDeviceInfoUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveOverlayPermissionUseCase
import pl.prodevcode.tvairplay.domain.usecase.RequestOverlayPermissionUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveReceiverStateUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveSettingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.PlaybackControlUseCase
import pl.prodevcode.tvairplay.domain.usecase.StartReceiverUseCase
import pl.prodevcode.tvairplay.domain.usecase.StopReceiverUseCase
import pl.prodevcode.tvairplay.domain.usecase.ToggleReceiverUseCase

@HiltViewModel
class ReceiverViewModel @Inject constructor(
    observeState: ObserveReceiverStateUseCase,
    observeDevice: ObserveDeviceInfoUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val observeOverlay: ObserveOverlayPermissionUseCase,
    private val requestOverlay: RequestOverlayPermissionUseCase,
    private val startReceiver: StartReceiverUseCase,
    private val toggleReceiver: ToggleReceiverUseCase,
    private val stopReceiver: StopReceiverUseCase,
    private val playback: PlaybackControlUseCase,
    private val surfaces: ReceiverRepository,
) : ViewModel() {

    val uiState: StateFlow<ReceiverUiState> = combine(
        observeState(), observeDevice(), observeSettings(), observeOverlay()
    ) { state, device, settings, overlay ->
        ReceiverUiState(
            receiver = state,
            device = device,
            keepScreenOn = state.connectedClients > 0,
            runInBackground = settings.runInBackground,
            // only relevant when we are expected to pop up on connect
            overlayPermissionGranted = overlay || !settings.openAppOnConnect,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceiverUiState())

    fun ensureStarted() = startReceiver()

    /** Called when the app leaves the foreground; never interrupts an active session. */
    fun onAppBackgrounded() {
        val ui = uiState.value
        if (!ui.runInBackground && ui.receiver.connectedClients == 0) stopReceiver()
    }
    fun toggleReceiver() = toggleReceiver(uiState.value.receiver.status)

    fun onPlayPause() = playback.togglePlayPause()
    fun onSeek(deltaMs: Long) = playback.seekBy(deltaMs)
    fun onSeekTo(positionMs: Long) = playback.seekTo(positionMs)
    fun onGrantOverlay() = requestOverlay()
    fun onResumed() = observeOverlay.refresh()
    fun onNext() = playback.next()
    fun onPrevious() = playback.previous()
    fun onStopVideo() = playback.stopVideo()

    fun onMirrorSurface(surface: Surface) = surfaces.attachMirrorSurface(surface)
    fun onMirrorSurfaceGone(surface: Surface) = surfaces.detachMirrorSurface(surface)
    fun onVideoSurface(surface: Surface) = surfaces.attachVideoSurface(surface)
    fun onVideoSurfaceGone(surface: Surface) = surfaces.detachVideoSurface(surface)

    val isPlayingMedia: Boolean
        get() = uiState.value.receiver.mode in setOf(SessionMode.VIDEO, SessionMode.AUDIO)
}
