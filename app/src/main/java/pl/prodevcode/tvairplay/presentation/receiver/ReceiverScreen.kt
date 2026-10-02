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
import pl.prodevcode.tvairplay.presentation.components.VideoSurface
import pl.prodevcode.tvairplay.presentation.receiver.screens.AudioSession
import pl.prodevcode.tvairplay.presentation.receiver.screens.IdleScreen
import pl.prodevcode.tvairplay.presentation.receiver.screens.PinOverlay
import pl.prodevcode.tvairplay.presentation.receiver.screens.VideoOverlay

@Composable
fun ReceiverScreen(
    onOpenSettings: () -> Unit,
    viewModel: ReceiverViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val state = ui.receiver

    LaunchedEffect(Unit) { viewModel.ensureStarted() }

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
                    onSurfaceAvailable = viewModel::onMirrorSurface,
                    onSurfaceDestroyed = viewModel::onMirrorSurfaceGone,
                )

                SessionMode.VIDEO -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    VideoSurface(
                        aspectRatio = state.video.aspectRatio,
                        onSurfaceAvailable = viewModel::onVideoSurface,
                        onSurfaceDestroyed = viewModel::onVideoSurfaceGone,
                    )
                    VideoOverlay(
                        video = state.video,
                        onPlayPause = viewModel::onPlayPause,
                        onSeekTo = viewModel::onSeekTo,
                        onStop = viewModel::onStopVideo,
                    )
                }

                SessionMode.AUDIO -> AudioSession(
                    nowPlaying = state.nowPlaying,
                    onPlayPause = viewModel::onPlayPause,
                    onNext = viewModel::onNext,
                    onPrevious = viewModel::onPrevious,
                )

                SessionMode.IDLE, SessionMode.CONNECTED -> IdleScreen(
                    state = state,
                    device = ui.device,
                    onToggleReceiver = viewModel::toggleReceiver,
                    onOpenSettings = onOpenSettings,
                    overlayPermissionGranted = ui.overlayPermissionGranted,
                    onGrantOverlay = viewModel::onGrantOverlay,
                )
            }
        }

        state.pin?.let { PinOverlay(pin = it) }
    }
}
