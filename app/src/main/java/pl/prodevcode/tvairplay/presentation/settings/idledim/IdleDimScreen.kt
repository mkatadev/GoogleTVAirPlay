package pl.prodevcode.tvairplay.presentation.settings.idledim

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.prodevcode.tvairplay.R
import pl.prodevcode.tvairplay.presentation.components.OptionRow
import pl.prodevcode.tvairplay.presentation.components.SettingsPage
import pl.prodevcode.tvairplay.presentation.mvi.CollectEffects
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import pl.prodevcode.tvairplay.presentation.theme.TvAirPlayTheme

@Composable
fun IdleDimScreen(onBack: () -> Unit, viewModel: IdleDimViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { if (it == IdleDimEffect.Close) onBack() }
    IdleDimContent(ui, viewModel::onIntent)
}

@Composable
private fun IdleDimContent(ui: IdleDimUiState, onIntent: (IdleDimIntent) -> Unit) {
    SettingsPage(
        title = stringResource(R.string.setting_idle_dim),
        subtitle = stringResource(R.string.idle_dim_subtitle),
        onBack = { onIntent(IdleDimIntent.Back) },
    ) {
        LazyColumn(
            Modifier.weight(1f).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ui.options) { minutes ->
                OptionRow(
                    title = idleDimLabel(minutes),
                    selected = minutes == ui.currentMinutes,
                    onClick = { onIntent(IdleDimIntent.Pick(minutes)) },
                )
            }
        }
    }
}

@Composable
fun idleDimLabel(minutes: Int): String =
    if (minutes <= 0) stringResource(R.string.idle_dim_never)
    else pluralStringResource(R.plurals.idle_dim_minutes, minutes, minutes)

@Preview(device = Devices.TV_1080p)
@Composable
private fun IdleDimPreview() {
    TvAirPlayTheme { IdleDimContent(IdleDimUiState(currentMinutes = 5), onIntent = {}) }
}
