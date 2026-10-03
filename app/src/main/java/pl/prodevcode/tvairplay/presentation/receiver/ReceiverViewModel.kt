package pl.prodevcode.tvairplay.presentation.receiver

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import pl.prodevcode.tvairplay.domain.usecase.ObserveDeviceInfoUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveOverlayPermissionUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveReceiverStateUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveSettingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.PlaybackControlUseCase
import pl.prodevcode.tvairplay.domain.usecase.RequestOverlayPermissionUseCase
import pl.prodevcode.tvairplay.domain.usecase.StartReceiverUseCase
import pl.prodevcode.tvairplay.domain.usecase.StopReceiverUseCase
import pl.prodevcode.tvairplay.domain.usecase.ToggleReceiverUseCase
import pl.prodevcode.tvairplay.platform.VideoSurfaceHost
import pl.prodevcode.tvairplay.presentation.mvi.MviViewModel
import pl.prodevcode.tvairplay.presentation.receiver.ReceiverIntent as Intent

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
    private val surfaces: VideoSurfaceHost,
) : MviViewModel<ReceiverUiState, Intent, ReceiverEffect>(ReceiverUiState()) {

    init {
        combine(observeState(), observeDevice(), observeSettings(), observeOverlay()) { state, device, settings, overlay ->
            ReceiverUiState(
                receiver = state,
                device = device,
                keepScreenOn = state.connectedClients > 0,
                runInBackground = settings.runInBackground,
                // only relevant when we are expected to pop up on connect
                overlayPermissionGranted = overlay || !settings.openAppOnConnect,
            )
        }.reduceInto { it }
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            Intent.EnsureStarted -> startReceiver()
            Intent.AppResumed -> observeOverlay.refresh()
            // leaving the foreground never interrupts an active session
            Intent.AppBackgrounded -> with(currentState) {
                if (!runInBackground && receiver.connectedClients == 0) stopReceiver()
            }
            Intent.ToggleReceiver -> toggleReceiver(currentState.receiver.status)
            Intent.GrantOverlay -> requestOverlay()

            Intent.PlayPause -> playback.togglePlayPause()
            is Intent.SeekBy -> playback.seekBy(intent.deltaMs)
            is Intent.SeekTo -> playback.seekTo(intent.positionMs)
            Intent.Next -> playback.next()
            Intent.Previous -> playback.previous()
            Intent.StopVideo -> playback.stopVideo()

            is Intent.MirrorSurfaceReady -> surfaces.attachMirrorSurface(intent.surface)
            is Intent.MirrorSurfaceGone -> surfaces.detachMirrorSurface(intent.surface)
            is Intent.VideoSurfaceReady -> surfaces.attachVideoSurface(intent.surface)
            is Intent.VideoSurfaceGone -> surfaces.detachVideoSurface(intent.surface)
        }
    }
}
