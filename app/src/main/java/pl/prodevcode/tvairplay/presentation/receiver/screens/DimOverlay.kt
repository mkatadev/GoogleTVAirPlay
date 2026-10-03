package pl.prodevcode.tvairplay.presentation.receiver.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.prodevcode.tvairplay.domain.model.NowPlaying
import pl.prodevcode.tvairplay.presentation.theme.AirPlayColors

/** Near-black veil over the now-playing screen after idling, with a faint track line so the TV still says what plays. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DimOverlay(nowPlaying: NowPlaying) {
    val alpha by animateFloatAsState(1f, animationSpec = tween(1_500), label = "dim")
    Box(
        Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }.background(Color.Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (nowPlaying.hasTrack) {
            Text(
                listOf(nowPlaying.title, nowPlaying.artist).filter { it.isNotBlank() }.joinToString(" · "),
                color = AirPlayColors.Muted.copy(alpha = 0.6f),
                fontSize = 18.sp,
                modifier = Modifier.padding(48.dp),
            )
        }
    }
}
