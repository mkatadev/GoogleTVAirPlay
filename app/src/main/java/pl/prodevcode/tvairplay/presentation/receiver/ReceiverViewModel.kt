package pl.prodevcode.tvairplay.presentation.receiver

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import pl.prodevcode.tvairplay.domain.model.SessionMode
import pl.prodevcode.tvairplay.domain.model.UpdateCheck
import pl.prodevcode.tvairplay.domain.usecase.CheckForUpdateUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveUpdateUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveDeviceInfoUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveOverlayPermissionUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveReceiverStateUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveSettingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.PinControlUseCase
import pl.prodevcode.tvairplay.domain.usecase.PlaybackControlUseCase
import pl.prodevcode.tvairplay.domain.usecase.RequestOverlayPermissionUseCase
import pl.prodevcode.tvairplay.domain.usecase.StartReceiverUseCase
import pl.prodevcode.tvairplay.domain.usecase.StopReceiverUseCase
import pl.prodevcode.tvairplay.domain.usecase.ToggleReceiverUseCase
import pl.prodevcode.tvairplay.platform.SubtitleCues
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
    private val pin: PinControlUseCase,
    private val surfaces: VideoSurfaceHost,
    observeUpdate: ObserveUpdateUseCase,
    private val checkForUpdate: CheckForUpdateUseCase,
    subtitleCues: SubtitleCues,
) : MviViewModel<ReceiverUiState, Intent, ReceiverEffect>(ReceiverUiState()) {

    private var idleTimer: Job? = null
    private var idleDimMinutes = 0

    init {
        combine(observeState(), observeDevice(), observeSettings(), observeOverlay()) { state, device, settings, overlay ->
            idleDimMinutes = settings.idleDimMinutes
            Pair(
                ReceiverUiState(
                    receiver = state,
                    device = device,
                    runInBackground = settings.runInBackground,
                    // only relevant when we are expected to pop up on connect
                    overlayPermissionGranted = overlay || !settings.openAppOnConnect,
                ),
                IdleKey(state.mode, state.nowPlaying.title, state.nowPlaying.playing),
            )
        }.reduceInto { (ui, key) ->
            // a new track, play/pause or a mode change counts as activity
            if (key != lastIdleKey) { lastIdleKey = key; restartIdleTimer(key.mode) }
            withDerived(
                ui.copy(dimmed = idleDimmed && key.mode == SessionMode.AUDIO, updateAvailable = latestRelease, cues = cues)
            )
        }
        subtitleCues.cues.reduceInto { copy(cues = it) }
        observeUpdate().reduceInto {
            latestRelease = (it as? UpdateCheck.Available)?.update?.latestVersion
            copy(updateAvailable = latestRelease)
        }
    }

    private var latestRelease: String? = null

    private data class IdleKey(val mode: SessionMode, val title: String, val playing: Boolean)
    private var lastIdleKey: IdleKey? = null
    private var idleDimmed = false

    private fun withDerived(ui: ReceiverUiState): ReceiverUiState {
        val paused = ui.receiver.mode == SessionMode.AUDIO && !ui.receiver.nowPlaying.playing
        return ui.copy(keepScreenOn = ui.receiver.connectedClients > 0 && !(ui.dimmed && paused))
    }

    private fun restartIdleTimer(mode: SessionMode) {
        idleTimer?.cancel()
        idleDimmed = false
        if (mode != SessionMode.AUDIO || idleDimMinutes <= 0) return
        idleTimer = viewModelScope.launch {
            delay(idleDimMinutes * 60_000L)
            idleDimmed = true
            setState { withDerived(copy(dimmed = true)) }
        }
    }

    private fun wake() {
        val wasDimmed = idleDimmed
        restartIdleTimer(currentState.receiver.mode)
        if (wasDimmed) setState { withDerived(copy(dimmed = false)) }
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            Intent.EnsureStarted -> {
                startReceiver()
                viewModelScope.launch { checkForUpdate() }
            }
            Intent.AppResumed -> observeOverlay.refresh()
            // leaving the foreground never interrupts an active session
            Intent.AppBackgrounded -> with(currentState) {
                if (!runInBackground && receiver.connectedClients == 0) stopReceiver()
            }
            Intent.ToggleReceiver -> toggleReceiver(currentState.receiver.status)
            Intent.GrantOverlay -> requestOverlay()
            Intent.UserInteraction -> wake()
            Intent.DismissPin -> pin.dismiss()

            Intent.PlayPause -> { wake(); playback.togglePlayPause() }
            is Intent.SeekBy -> playback.seekBy(intent.deltaMs)
            is Intent.SeekTo -> playback.seekTo(intent.positionMs)
            Intent.Next -> playback.next()
            Intent.Previous -> playback.previous()
            Intent.StopVideo -> playback.stopVideo()
            is Intent.SelectAudioTrack -> playback.selectAudioTrack(intent.id)
            is Intent.SelectSubtitleTrack -> playback.selectSubtitleTrack(intent.id)

            is Intent.MirrorSurfaceReady -> surfaces.attachMirrorSurface(intent.surface)
            is Intent.MirrorSurfaceGone -> surfaces.detachMirrorSurface(intent.surface)
            is Intent.VideoSurfaceReady -> surfaces.attachVideoSurface(intent.surface)
            is Intent.VideoSurfaceGone -> surfaces.detachVideoSurface(intent.surface)
        }
    }
}
