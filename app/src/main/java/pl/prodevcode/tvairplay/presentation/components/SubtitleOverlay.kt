package pl.prodevcode.tvairplay.presentation.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView

/** Media3's subtitle renderer (positioning, styling, caption settings) over the video surface. */
// SubtitleView is still @UnstableApi in media3-ui; it is the only caption renderer that honours system caption settings
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SubtitleOverlay(cues: List<Cue>, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            SubtitleView(ctx).apply {
                setUserDefaultStyle()
                setUserDefaultTextSize()
            }
        },
        update = { it.setCues(cues) },
        modifier = modifier.fillMaxSize(),
    )
}
