package pl.prodevcode.tvairplay.presentation.components

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItemColors
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnit.Companion.Unspecified
import pl.prodevcode.tvairplay.presentation.theme.AirPlayColors

/** Focus = primary blue with dark content, so every line stays legible in both states. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun appListItemColors(): ListItemColors = ListItemDefaults.colors(
    containerColor = AirPlayColors.Surface.copy(alpha = 0.6f),
    contentColor = AirPlayColors.OnSurface,
    focusedContainerColor = AirPlayColors.Primary,
    focusedContentColor = AirPlayColors.OnPrimary,
    pressedContainerColor = AirPlayColors.Primary,
    pressedContentColor = AirPlayColors.OnPrimary,
    selectedContainerColor = AirPlayColors.SurfaceVariant,
    selectedContentColor = AirPlayColors.OnSurface,
    focusedSelectedContainerColor = AirPlayColors.Primary,
    focusedSelectedContentColor = AirPlayColors.OnPrimary,
)

/** Secondary line that follows the item's current content color instead of a fixed hue. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SupportingText(text: String, fontSize: TextUnit = Unspecified) {
    Text(text, color = LocalContentColor.current.copy(alpha = 0.72f), fontSize = fontSize)
}

