package pl.prodevcode.tvairplay.presentation.receiver.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import pl.prodevcode.tvairplay.R
import pl.prodevcode.tvairplay.domain.model.PinRequest
import pl.prodevcode.tvairplay.presentation.theme.AirPlayColors

/** PIN prompt shown above every screen with a small countdown ring; Cancel or Back hides it. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PinOverlay(request: PinRequest, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(request.pin) { focus.requestFocus() }
    BackHandler(onBack = onDismiss)

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(request.pin) {
        while (true) { now = System.currentTimeMillis(); delay(200) }
    }
    val total = (request.expiresAtMs - request.shownAtMs).coerceAtLeast(1)
    val left = (request.expiresAtMs - now).coerceIn(0, total)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            // keep d-pad/media keys from reaching the screen underneath; Back and the button dismiss
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.Back -> { if (event.type == KeyEventType.KeyUp) onDismiss(); true }
                    Key.DirectionCenter, Key.Enter -> false
                    else -> true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .background(AirPlayColors.Surface, RoundedCornerShape(24.dp))
                .padding(horizontal = 64.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pin_title), fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Box(contentAlignment = Alignment.Center) {
                    CountdownRing(fraction = left.toFloat() / total, modifier = Modifier.size(40.dp))
                    Text("${(left + 999) / 1000}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AirPlayColors.Muted)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                request.pin.chunked(1).joinToString("  "),
                fontSize = 72.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(28.dp))
            Button(onClick = onDismiss, modifier = Modifier.focusRequester(focus)) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

@Composable
private fun CountdownRing(fraction: Float, modifier: Modifier = Modifier) {
    val track = AirPlayColors.SurfaceVariant
    val color = if (fraction > 0.25f) AirPlayColors.Primary else Color(0xFFFF5252)
    Canvas(modifier) {
        val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        val inset = stroke.width / 2
        val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
        drawArc(color, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = stroke)
    }
}
