package pl.prodevcode.tvairplay.presentation.receiver

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.prodevcode.tvairplay.domain.model.SessionMode
import pl.prodevcode.tvairplay.presentation.components.SubtitleOverlay
import pl.prodevcode.tvairplay.presentation.components.VideoSurface
import pl.prodevcode.tvairplay.presentation.receiver.screens.AudioSession
import pl.prodevcode.tvairplay.presentation.receiver.screens.DimOverlay
import pl.prodevcode.tvairplay.presentation.receiver.screens.IdleScreen
import pl.prodevcode.tvairplay.presentation.receiver.screens.VideoOverlay

@Composable
fun ReceiverScreen(
    onOpenSettings: () -> Unit,
    viewModel: ReceiverViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    ReceiverContent(ui = ui, onIntent = viewModel::onIntent, onOpenSettings = onOpenSettings)
}

@Composable
private fun ReceiverContent(
    ui: ReceiverUiState,
    onIntent: (ReceiverIntent) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state = ui.receiver

    LaunchedEffect(Unit) { onIntent(ReceiverIntent.EnsureStarted) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = state.mode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "session",
        ) { mode ->
            when (mode) {
                SessionMode.MIRRORING -> VideoSurface(
                    aspectRatio = state.mirrorAspectRatio,
                    onSurfaceAvailable = { onIntent(ReceiverIntent.MirrorSurfaceReady(it)) },
                    onSurfaceDestroyed = { onIntent(ReceiverIntent.MirrorSurfaceGone(it)) },
                )

                SessionMode.VIDEO -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    VideoSurface(
                        aspectRatio = state.video.aspectRatio,
                        onSurfaceAvailable = { onIntent(ReceiverIntent.VideoSurfaceReady(it)) },
                        onSurfaceDestroyed = { onIntent(ReceiverIntent.VideoSurfaceGone(it)) },
                    )
                    SubtitleOverlay(cues = ui.cues)
                    VideoOverlay(
                        video = state.video,
                        onPlayPause = { onIntent(ReceiverIntent.PlayPause) },
                        onSeekTo = { onIntent(ReceiverIntent.SeekTo(it)) },
                        onStop = { onIntent(ReceiverIntent.StopVideo) },
                        onSelectAudioTrack = { onIntent(ReceiverIntent.SelectAudioTrack(it)) },
                        onSelectSubtitleTrack = { onIntent(ReceiverIntent.SelectSubtitleTrack(it)) },
                    )
                }

                SessionMode.AUDIO -> AudioSession(
                    nowPlaying = state.nowPlaying,
                    onPlayPause = { onIntent(ReceiverIntent.PlayPause) },
                    onNext = { onIntent(ReceiverIntent.Next) },
                    onPrevious = { onIntent(ReceiverIntent.Previous) },
                )

                SessionMode.IDLE, SessionMode.CONNECTED -> IdleScreen(
                    state = state,
                    device = ui.device,
                    updateAvailable = ui.updateAvailable,
                    onToggleReceiver = { onIntent(ReceiverIntent.ToggleReceiver) },
                    onOpenSettings = onOpenSettings,
                    overlayPermissionGranted = ui.overlayPermissionGranted,
                    onGrantOverlay = { onIntent(ReceiverIntent.GrantOverlay) },
                )
            }
        }

        if (ui.dimmed) DimOverlay(nowPlaying = state.nowPlaying)
    }
}
