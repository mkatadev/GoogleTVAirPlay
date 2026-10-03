package pl.prodevcode.tvairplay.presentation.settings.devicename

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.prodevcode.tvairplay.R
import pl.prodevcode.tvairplay.presentation.components.OptionRow
import pl.prodevcode.tvairplay.presentation.components.SectionHeader
import pl.prodevcode.tvairplay.presentation.components.SettingsPage
import pl.prodevcode.tvairplay.presentation.mvi.CollectEffects
import pl.prodevcode.tvairplay.presentation.theme.AirPlayColors
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import pl.prodevcode.tvairplay.presentation.theme.TvAirPlayTheme

@Composable
fun DeviceNameScreen(onBack: () -> Unit, viewModel: DeviceNameViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { if (it == DeviceNameEffect.Close) onBack() }
    DeviceNameContent(ui, viewModel::onIntent)
}

/** Free text via the TV keyboard, plus localized suggestions; focus starts on the current suggestion so no keyboard pops up. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DeviceNameContent(ui: DeviceNameUiState, onIntent: (DeviceNameIntent) -> Unit) {
    val presets = stringArrayResource(R.array.device_name_presets).toList()

    SettingsPage(
        title = stringResource(R.string.setting_device_name),
        subtitle = stringResource(R.string.device_name_hint),
        onBack = { onIntent(DeviceNameIntent.Back) },
    ) {
        LazyColumn(
            Modifier.weight(1f).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = ui.text,
                    onValueChange = { onIntent(DeviceNameIntent.DraftChanged(it)) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.device_name_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onIntent(DeviceNameIntent.Save) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AirPlayColors.OnSurface, unfocusedTextColor = AirPlayColors.OnSurface,
                        focusedBorderColor = AirPlayColors.Primary, unfocusedBorderColor = AirPlayColors.Muted,
                        focusedLabelColor = AirPlayColors.Primary, unfocusedLabelColor = AirPlayColors.Muted,
                        cursorColor = AirPlayColors.Primary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = { onIntent(DeviceNameIntent.Save) }, enabled = ui.canSave) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)); SectionHeader(stringResource(R.string.device_name_suggestions)) }
            items(presets) { name ->
                OptionRow(title = name, selected = name == ui.current, onClick = { onIntent(DeviceNameIntent.Pick(name)) })
            }
        }
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun DeviceNamePreview() {
    TvAirPlayTheme { DeviceNameContent(DeviceNameUiState(current = "Google TV", draft = "Living Room TV"), onIntent = {}) }
}
