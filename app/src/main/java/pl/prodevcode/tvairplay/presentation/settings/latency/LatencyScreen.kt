package pl.prodevcode.tvairplay.presentation.settings.latency

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.prodevcode.tvairplay.R
import pl.prodevcode.tvairplay.domain.model.LatencyMode
import pl.prodevcode.tvairplay.presentation.components.OptionRow
import pl.prodevcode.tvairplay.presentation.components.SettingsPage
import pl.prodevcode.tvairplay.presentation.mvi.CollectEffects
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import pl.prodevcode.tvairplay.presentation.theme.TvAirPlayTheme

@Composable
fun LatencyScreen(onBack: () -> Unit, viewModel: LatencyViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { if (it == LatencyEffect.Close) onBack() }
    LatencyContent(ui, viewModel::onIntent)
}

@Composable
private fun LatencyContent(ui: LatencyUiState, onIntent: (LatencyIntent) -> Unit) {
    SettingsPage(
        title = stringResource(R.string.setting_latency),
        subtitle = stringResource(R.string.latency_subtitle),
        onBack = { onIntent(LatencyIntent.Back) },
    ) {
        LazyColumn(
            Modifier.weight(1f).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(LatencyMode.entries) { mode ->
                OptionRow(
                    title = stringResource(latencyModeTitle(mode)),
                    description = stringResource(latencyModeDesc(mode)),
                    selected = mode == ui.current,
                    onClick = { onIntent(LatencyIntent.Pick(mode)) },
                )
            }
        }
    }
}

fun latencyModeTitle(mode: LatencyMode) = when (mode) {
    LatencyMode.LOW -> R.string.latency_low
    LatencyMode.BALANCED -> R.string.latency_balanced
    LatencyMode.SMOOTH -> R.string.latency_smooth
}

private fun latencyModeDesc(mode: LatencyMode) = when (mode) {
    LatencyMode.LOW -> R.string.latency_low_desc
    LatencyMode.BALANCED -> R.string.latency_balanced_desc
    LatencyMode.SMOOTH -> R.string.latency_smooth_desc
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun LatencyPreview() {
    TvAirPlayTheme { LatencyContent(LatencyUiState(current = LatencyMode.SMOOTH), onIntent = {}) }
}
