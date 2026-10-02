package pl.prodevcode.tvairplay.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object AirPlayColors {
    val Background = Color(0xFF0B0F17)
    val Surface = Color(0xFF151B27)
    val SurfaceVariant = Color(0xFF1E2636)
    val Primary = Color(0xFF7CC4FF)
    val OnPrimary = Color(0xFF00324F)
    val Secondary = Color(0xFFB8C7E0)
    val Accent = Color(0xFF5EE1B6)
    val Error = Color(0xFFFF7A8A)
    val OnSurface = Color(0xFFE6EAF2)
    val Muted = Color(0xFF8A96AC)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAirPlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AirPlayColors.Primary,
            onPrimary = AirPlayColors.OnPrimary,
            secondary = AirPlayColors.Secondary,
            tertiary = AirPlayColors.Accent,
            background = AirPlayColors.Background,
            onBackground = AirPlayColors.OnSurface,
            surface = AirPlayColors.Surface,
            onSurface = AirPlayColors.OnSurface,
            surfaceVariant = AirPlayColors.SurfaceVariant,
            onSurfaceVariant = AirPlayColors.Muted,
            error = AirPlayColors.Error,
        ),
    ) {
        // tv-material does not seed LocalContentColor outside Surface; default text would render black
        CompositionLocalProvider(LocalContentColor provides AirPlayColors.OnSurface, content = content)
    }
}
