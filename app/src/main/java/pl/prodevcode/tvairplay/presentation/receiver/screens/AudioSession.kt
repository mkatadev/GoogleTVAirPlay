package pl.prodevcode.tvairplay.presentation.receiver.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import pl.prodevcode.tvairplay.domain.model.NowPlaying
import pl.prodevcode.tvairplay.presentation.components.ProgressTrack
import pl.prodevcode.tvairplay.presentation.components.rememberCoverArt
import pl.prodevcode.tvairplay.presentation.components.formatTime
import pl.prodevcode.tvairplay.presentation.theme.AirPlayColors

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AudioSession(
    nowPlaying: NowPlaying,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    // sender only reports position on discontinuities; extrapolate while playing
    var position by remember(nowPlaying.positionMs, nowPlaying.playing) { mutableLongStateOf(nowPlaying.positionMs) }
    LaunchedEffect(nowPlaying.positionMs, nowPlaying.playing) {
        while (nowPlaying.playing) {
            delay(500)
            position = (position + 500).coerceAtMost(nowPlaying.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
        }
    }

    val art = rememberCoverArt(nowPlaying.coverArt)

    Box(Modifier.fillMaxSize().background(AirPlayColors.Background)) {
        art?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(90.dp),
                alpha = 0.45f,
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, AirPlayColors.Background.copy(alpha = 0.9f)))
            )
        )

        Row(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 96.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(320.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AirPlayColors.Surface),
                contentAlignment = Alignment.Center,
            ) {
                if (art != null) {
                    Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.MusicNote, null, Modifier.size(96.dp), tint = AirPlayColors.Muted)
                }
            }
            Spacer(Modifier.width(56.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    nowPlaying.title.ifBlank { "—" },
                    fontSize = 40.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Text(nowPlaying.artist, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (nowPlaying.album.isNotBlank()) {
                    Text(nowPlaying.album, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(32.dp))
                ProgressTrack(position = position, duration = nowPlaying.durationMs, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(position), fontSize = 14.sp, color = AirPlayColors.Muted)
                    Text(formatTime(nowPlaying.durationMs), fontSize = 14.sp, color = AirPlayColors.Muted)
                }
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, null) }
                    IconButton(onClick = onPlayPause) {
                        Icon(if (nowPlaying.playing) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                    }
                    IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null) }
                }
            }
        }
    }
}
