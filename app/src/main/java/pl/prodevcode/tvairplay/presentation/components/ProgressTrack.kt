package pl.prodevcode.tvairplay.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pl.prodevcode.tvairplay.presentation.theme.AirPlayColors

/**
 * @param secondaryPosition when >= 0, a faint marker for the *current* playback position while the
 *   main bar shows a pending seek target.
 */
@Composable
fun ProgressTrack(
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
    secondaryPosition: Long = -1L,
    emphasized: Boolean = false,
    segments: Int = 0,
) {
    fun frac(p: Long) = if (duration > 0) (p.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val height by animateDpAsState(if (emphasized) 10.dp else 6.dp, label = "trackHeight")

    BoxWithConstraints(modifier.height(20.dp), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(5.dp))
                .background(AirPlayColors.SurfaceVariant)
        ) {
            if (secondaryPosition >= 0) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(frac(secondaryPosition))
                        .background(AirPlayColors.Muted.copy(alpha = 0.5f))
                )
            }
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(frac(position))
                    .background(AirPlayColors.Primary, RoundedCornerShape(5.dp))
            )
        }
        if (segments > 1) {
            val tick = 2.dp
            for (i in 1 until segments) {
                Box(
                    Modifier
                        .offset(x = (maxWidth - tick) * (i.toFloat() / segments))
                        .size(width = tick, height = height + 6.dp)
                        .background(Color.White.copy(alpha = 0.45f))
                )
            }
        }
        if (emphasized) {
            val knob = 18.dp
            Box(
                Modifier
                    .offset(x = (maxWidth - knob) * frac(position))
                    .size(knob)
                    .background(AirPlayColors.Primary, CircleShape)
            )
        }
    }
}
