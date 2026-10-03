package pl.prodevcode.tvairplay.presentation.settings.trusted

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.text.DateFormat
import java.util.Date
import pl.prodevcode.tvairplay.R
import pl.prodevcode.tvairplay.presentation.components.SettingsPage
import pl.prodevcode.tvairplay.presentation.components.SupportingText
import pl.prodevcode.tvairplay.presentation.components.appListItemColors
import pl.prodevcode.tvairplay.presentation.mvi.CollectEffects
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import pl.prodevcode.tvairplay.presentation.theme.TvAirPlayTheme
import pl.prodevcode.tvairplay.domain.model.TrustedDevice

@Composable
fun TrustedDevicesScreen(onBack: () -> Unit, viewModel: TrustedDevicesViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { if (it == TrustedDevicesEffect.Close) onBack() }
    TrustedDevicesContent(ui, viewModel::onIntent)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrustedDevicesContent(ui: TrustedDevicesUiState, onIntent: (TrustedDevicesIntent) -> Unit) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val firstFocus = remember { FocusRequester() }
    val hasDevices = ui.devices.isNotEmpty()
    LaunchedEffect(hasDevices) { if (hasDevices) firstFocus.requestFocus() }

    SettingsPage(
        title = stringResource(R.string.setting_trusted_devices),
        subtitle = stringResource(R.string.trusted_devices_subtitle),
        onBack = { onIntent(TrustedDevicesIntent.Back) },
    ) {
        LazyColumn(
            Modifier.weight(1f).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!hasDevices) {
                item {
                    Text(
                        stringResource(R.string.trusted_devices_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            itemsIndexed(ui.devices, key = { _, d -> d.id }) { index, d ->
                ListItem(
                    selected = false,
                    onClick = { onIntent(TrustedDevicesIntent.Forget(d.id)) },
                    headlineContent = { Text(d.name) },
                    supportingContent = {
                        SupportingText(stringResource(R.string.trusted_device_last_seen, dateFormat.format(Date(d.lastSeenAt))))
                    },
                    trailingContent = { SupportingText(stringResource(R.string.trusted_device_forget), fontSize = 12.sp) },
                    colors = appListItemColors(),
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
            if (hasDevices) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(onClick = { onIntent(TrustedDevicesIntent.Forget(null)) }) {
                            Text(stringResource(R.string.trusted_devices_forget_all))
                        }
                    }
                }
            }
        }
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun TrustedDevicesPreview() {
    TvAirPlayTheme {
        TrustedDevicesContent(
            TrustedDevicesUiState(
                devices = listOf(
                    TrustedDevice("1", "Ania's iPhone", 0L, 1_790_000_000_000L),
                    TrustedDevice("2", "MacBook Pro", 0L, 1_789_000_000_000L),
                ),
            ),
            onIntent = {},
        )
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun TrustedDevicesEmptyPreview() {
    TvAirPlayTheme { TrustedDevicesContent(TrustedDevicesUiState(), onIntent = {}) }
}
