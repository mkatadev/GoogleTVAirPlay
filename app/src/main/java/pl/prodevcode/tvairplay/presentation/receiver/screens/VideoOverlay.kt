package pl.prodevcode.tvairplay.presentation.receiver.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.prodevcode.tvairplay.R
import pl.prodevcode.tvairplay.domain.model.VideoPlayback
import pl.prodevcode.tvairplay.presentation.components.ProgressTrack
import pl.prodevcode.tvairplay.presentation.components.formatTime
import pl.prodevcode.tvairplay.presentation.theme.AirPlayColors

private const val SEEK_STEP_MS = 10_000L
private const val SEEK_STEP_FAST_MS = 30_000L
private const val SEEK_STEP_TURBO_MS = 60_000L
private const val SEEK_COMMIT_DELAY_MS = 600L
private const val AUTO_HIDE_MS = 4_000L
private const val JUMP_SEGMENTS = 10

private val digitKeys = listOf(
    Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine,
)
private val numPadKeys = listOf(
    Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4,
    Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9,
)

private fun digitOf(key: Key): Int? =
    digitKeys.indexOf(key).takeIf { it >= 0 } ?: numPadKeys.indexOf(key).takeIf { it >= 0 }

/**
 * D-pad transport for AirPlay video. Seeking works like a TV player: every ◀/▶ press (or hold — key
 * repeats) moves a *pending* position shown on the bar; the seek is committed once the user pauses
 * for [SEEK_COMMIT_DELAY_MS]. Step size scales with the video length and grows while holding.
 * ▲/▼ jump ±10 %, digits 0–9 jump to 0–90 %. OK = play/pause, Back = hide / stop.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoOverlay(
    video: VideoPlayback,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onStop: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var visible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableLongStateOf(0L) }

    // pending seek target; -1 = none
    var pendingMs by remember { mutableLongStateOf(-1L) }
    var pendingDir by remember { mutableStateOf(0) }
    var repeatCount by remember { mutableStateOf(0) }
    var commitJob by remember { mutableStateOf<Job?>(null) }

    val seeking = pendingMs >= 0
    val shownPosition = if (seeking) pendingMs else video.positionMs

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(interactionTick, video.playing, seeking) {
        visible = true
        if (video.playing && !seeking) {
            delay(AUTO_HIDE_MS)
            visible = false
        }
    }

    fun seekPending(target: Long, direction: Int) {
        interactionTick++
        val max = video.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
        pendingDir = direction
        pendingMs = target.coerceIn(0L, max)
        commitJob?.cancel()
        commitJob = scope.launch {
            delay(SEEK_COMMIT_DELAY_MS)
            onSeekTo(pendingMs)
            pendingMs = -1
            pendingDir = 0
            repeatCount = 0
        }
    }

    fun pendingBase() = if (pendingMs >= 0) pendingMs else video.positionMs

    // steps scale with the video length so the bar visibly moves even on long videos
    fun step(direction: Int) {
        repeatCount = if (direction == pendingDir) repeatCount + 1 else 0
        val d = video.durationMs.coerceAtLeast(0L)
        val step = when {
            repeatCount >= 12 -> maxOf(SEEK_STEP_TURBO_MS, d / 20)
            repeatCount >= 4 -> maxOf(SEEK_STEP_FAST_MS, d / 40)
            else -> maxOf(SEEK_STEP_MS, d / 100)
        }
        seekPending(pendingBase() + direction * step, direction)
    }

    fun jumpBy(direction: Int) {
        val d = video.durationMs
        val jump = if (d > 0) d / JUMP_SEGMENTS else SEEK_STEP_TURBO_MS
        repeatCount = 0
        seekPending(pendingBase() + direction * jump, direction)
    }

    fun jumpToSegment(segment: Int) {
        val d = video.durationMs.takeIf { it > 0 } ?: return
        repeatCount = 0
        val target = d * segment / JUMP_SEGMENTS
        seekPending(target, if (target >= pendingBase()) 1 else -1)
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                when (event.type) {
                    // repeats arrive as KeyDown while held → smooth scrubbing
                    KeyEventType.KeyDown -> when (event.key) {
                        Key.DirectionRight, Key.MediaFastForward, Key.MediaSkipForward -> { step(+1); true }
                        Key.DirectionLeft, Key.MediaRewind, Key.MediaSkipBackward -> { step(-1); true }
                        // first ▲/▼ only reveals the overlay, so a stray press doesn't jump
                        Key.DirectionUp, Key.ChannelUp, Key.PageUp -> {
                            if (visible) jumpBy(+1) else interactionTick++; true
                        }
                        Key.DirectionDown, Key.ChannelDown, Key.PageDown -> {
                            if (visible) jumpBy(-1) else interactionTick++; true
                        }
                        else -> digitOf(event.key)?.let { jumpToSegment(it); true } ?: false
                    }
                    KeyEventType.KeyUp -> when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                            interactionTick++; onPlayPause(); true
                        }
                        Key.DirectionRight, Key.DirectionLeft, Key.MediaFastForward, Key.MediaRewind,
                        Key.MediaSkipForward, Key.MediaSkipBackward -> true
                        Key.MediaStop -> { onStop(); true }
                        Key.Back -> { if (visible) { visible = false; true } else { onStop(); true } }
                        Key.DirectionUp, Key.DirectionDown, Key.ChannelUp, Key.ChannelDown,
                        Key.PageUp, Key.PageDown -> true
                        else -> digitOf(event.key) != null
                    }
                    else -> false
                }
            }
    ) {
        if (video.buffering && !seeking) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(64.dp), color = AirPlayColors.Primary)
        }

        // big transient glyph while seeking / toggling
        AnimatedVisibility(seeking, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            Box(
                Modifier.size(112.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (pendingDir >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                    null, Modifier.size(64.dp), tint = Color.White,
                )
            }
        }

        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .padding(horizontal = 56.dp, vertical = 40.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (video.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null, Modifier.size(28.dp), tint = Color.White,
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        video.title.ifBlank { stringResource(R.string.video_title_fallback) },
                        fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, color = Color.White,
                    )
                }
                Spacer(Modifier.height(16.dp))
                ProgressTrack(
                    position = shownPosition,
                    duration = video.durationMs,
                    modifier = Modifier.fillMaxWidth(),
                    secondaryPosition = if (seeking) video.positionMs else -1L,
                    emphasized = seeking,
                    segments = if (seeking) JUMP_SEGMENTS else 0,
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        formatTime(shownPosition),
                        fontSize = if (seeking) 18.sp else 14.sp,
                        fontWeight = if (seeking) FontWeight.Bold else FontWeight.Normal,
                        color = if (seeking) AirPlayColors.Primary else AirPlayColors.Muted,
                    )
                    Text(
                        stringResource(
                            when {
                                seeking -> R.string.video_hint_seeking
                                video.playing -> R.string.video_hint_playing
                                else -> R.string.video_hint_paused
                            }
                        ),
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(formatTime(video.durationMs), fontSize = 14.sp, color = AirPlayColors.Muted)
                }
            }
        }
    }
}
