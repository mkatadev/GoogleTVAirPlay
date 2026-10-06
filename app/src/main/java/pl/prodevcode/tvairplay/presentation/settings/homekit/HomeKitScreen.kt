package pl.prodevcode.tvairplay.presentation.settings.homekit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import pl.prodevcode.tvairplay.R
import pl.prodevcode.tvairplay.domain.model.HomeKitStatus
import pl.prodevcode.tvairplay.presentation.components.SectionHeader
import pl.prodevcode.tvairplay.presentation.components.SettingsPage
import pl.prodevcode.tvairplay.presentation.components.ToggleRow
import pl.prodevcode.tvairplay.presentation.components.ValueRow
import pl.prodevcode.tvairplay.presentation.mvi.CollectEffects
import pl.prodevcode.tvairplay.presentation.theme.TvAirPlayTheme

@Composable
fun HomeKitScreen(onBack: () -> Unit, viewModel: HomeKitViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { if (it == HomeKitEffect.Close) onBack() }
    HomeKitContent(ui, viewModel::onIntent)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeKitContent(ui: HomeKitUiState, onIntent: (HomeKitIntent) -> Unit) {
    val status = ui.status
    SettingsPage(
        title = stringResource(R.string.setting_homekit),
        subtitle = stringResource(R.string.homekit_subtitle),
        onBack = { onIntent(HomeKitIntent.Back) },
    ) {
        LazyColumn(
            Modifier.weight(1f).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ToggleRow(stringResource(R.string.homekit_enable), stringResource(R.string.homekit_enable_desc), ui.enabled) {
                    onIntent(HomeKitIntent.SetEnabled(it))
                }
            }
            item { ValueRow(stringResource(R.string.setting_homekit), homeKitSummary(ui.enabled, status), onClick = {}) }

            val code = status.setupCode
            if (ui.enabled && code != null) {
                item { SectionHeader(stringResource(R.string.homekit_setup_code)) }
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(code, fontSize = 64.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 6.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.homekit_setup_code_hint, ui.deviceName),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                        )
                    }
                }
            }

            if (status.paired) {
                item { SectionHeader(stringResource(R.string.section_security)) }
                item {
                    ValueRow(stringResource(R.string.homekit_reset), stringResource(R.string.homekit_reset_desc), onClick = {
                        onIntent(HomeKitIntent.ResetPairings)
                    })
                }
                if (ui.confirmReset) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(onClick = { onIntent(HomeKitIntent.CancelReset) }) { Text(stringResource(R.string.action_cancel)) }
                            Spacer(Modifier.width(16.dp))
                            Button(onClick = { onIntent(HomeKitIntent.ConfirmReset) }) { Text(stringResource(R.string.homekit_reset_confirm)) }
                        }
                    }
                }
            }

            if (status.accessoryId.isNotEmpty()) {
                item { ValueRow(stringResource(R.string.homekit_accessory_id), status.accessoryId, onClick = {}) }
            }
        }
    }
}

/** One-line state for the settings hub and this screen. */
@Composable
fun homeKitSummary(enabled: Boolean, status: HomeKitStatus): String = when {
    !enabled -> stringResource(R.string.homekit_status_off)
    !status.running -> stringResource(R.string.homekit_status_starting)
    !status.paired -> stringResource(R.string.homekit_status_unpaired)
    else -> pluralStringResource(R.plurals.homekit_status_paired, status.controllers, status.controllers)
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun HomeKitUnpairedPreview() {
    TvAirPlayTheme {
        HomeKitContent(
            HomeKitUiState(
                enabled = true,
                deviceName = "Living room TV",
                status = HomeKitStatus(running = true, setupCode = "031-45-154", accessoryId = "1A:2B:3C:4D:5E:6F"),
            ),
            onIntent = {},
        )
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun HomeKitPairedPreview() {
    TvAirPlayTheme {
        HomeKitContent(
            HomeKitUiState(
                enabled = true,
                status = HomeKitStatus(running = true, paired = true, controllers = 2, accessoryId = "1A:2B:3C:4D:5E:6F"),
                confirmReset = true,
            ),
            onIntent = {},
        )
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun HomeKitOffPreview() {
    TvAirPlayTheme { HomeKitContent(HomeKitUiState(), onIntent = {}) }
}
