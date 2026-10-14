package pl.prodevcode.tvairplay.presentation.settings.musicdelay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.prodevcode.tvairplay.R
import pl.prodevcode.tvairplay.domain.model.MusicDelay
import pl.prodevcode.tvairplay.presentation.components.OptionRow
import pl.prodevcode.tvairplay.presentation.components.SettingsPage
import pl.prodevcode.tvairplay.presentation.mvi.CollectEffects
import pl.prodevcode.tvairplay.presentation.theme.TvAirPlayTheme

@Composable
fun MusicDelayScreen(onBack: () -> Unit, viewModel: MusicDelayViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { if (it == MusicDelayEffect.Close) onBack() }
    MusicDelayContent(ui, viewModel::onIntent)
}

@Composable
private fun MusicDelayContent(ui: MusicDelayUiState, onIntent: (MusicDelayIntent) -> Unit) {
    SettingsPage(
        title = stringResource(R.string.setting_music_delay),
        subtitle = stringResource(R.string.music_delay_subtitle),
        onBack = { onIntent(MusicDelayIntent.Back) },
    ) {
        LazyColumn(
            Modifier.weight(1f).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(MusicDelay.entries) { delay ->
                OptionRow(
                    title = stringResource(musicDelayTitle(delay)),
                    description = stringResource(musicDelayDesc(delay)),
                    selected = delay == ui.current,
                    onClick = { onIntent(MusicDelayIntent.Pick(delay)) },
                )
            }
        }
    }
}

fun musicDelayTitle(delay: MusicDelay) = when (delay) {
    MusicDelay.SHORT -> R.string.music_delay_short
    MusicDelay.MEDIUM -> R.string.music_delay_medium
    MusicDelay.AIRPLAY -> R.string.music_delay_airplay
}

private fun musicDelayDesc(delay: MusicDelay) = when (delay) {
    MusicDelay.SHORT -> R.string.music_delay_short_desc
    MusicDelay.MEDIUM -> R.string.music_delay_medium_desc
    MusicDelay.AIRPLAY -> R.string.music_delay_airplay_desc
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun MusicDelayPreview() {
    TvAirPlayTheme { MusicDelayContent(MusicDelayUiState(current = MusicDelay.AIRPLAY), onIntent = {}) }
}
