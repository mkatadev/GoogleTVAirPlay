package pl.prodevcode.tvairplay.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import pl.prodevcode.tvairplay.R
import java.text.DateFormat
import java.util.Date
import pl.prodevcode.tvairplay.domain.model.IDLE_DIM_OPTIONS
import pl.prodevcode.tvairplay.domain.model.LatencyMode
import pl.prodevcode.tvairplay.domain.model.ReceiverSettings
import pl.prodevcode.tvairplay.domain.model.TrustedDevice
import pl.prodevcode.tvairplay.presentation.components.SupportingText
import pl.prodevcode.tvairplay.presentation.components.appListItemColors
import pl.prodevcode.tvairplay.presentation.theme.AirPlayColors

private val DEVICE_NAME_PRESETS = listOf("Google TV", "Salon TV", "Sypialnia TV", "Kuchnia TV", "Living Room", "Bedroom")

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    SettingsContent(
        ui = ui, onIntent = viewModel::onIntent,
        onBack = onBack, onOpenLicenses = onOpenLicenses, onOpenDiagnostics = onOpenDiagnostics,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsContent(
    ui: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val s = ui.settings
    LifecycleResumeEffect(Unit) { onIntent(SettingsIntent.ScreenResumed); onPauseOrDispose { } }

    Row(Modifier.fillMaxSize().background(AirPlayColors.Background)) {
        Column(
            Modifier.width(360.dp).fillMaxSize().background(AirPlayColors.Surface).padding(40.dp),
        ) {
            Text(stringResource(R.string.action_settings), fontSize = 36.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.settings_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Button(onClick = onBack) { Text(stringResource(R.string.action_back)) }
        }

        if (ui.pickingDeviceName) {
            DeviceNamePicker(current = s.deviceName) { onIntent(SettingsIntent.DeviceNamePicked(it)) }
            return@Row
        }
        if (ui.pickingIdleDim) {
            BackHandler { onIntent(SettingsIntent.IdleDimPicked(null)) }
            OptionPicker(
                title = stringResource(R.string.setting_idle_dim),
                options = IDLE_DIM_OPTIONS,
                current = s.idleDimMinutes,
                label = { idleDimLabel(it) },
                onPicked = { onIntent(SettingsIntent.IdleDimPicked(it)) },
            )
            return@Row
        }
        if (ui.pickingLatencyMode) {
            BackHandler { onIntent(SettingsIntent.LatencyModePicked(null)) }
            LatencyModePicker(current = s.latencyMode) { onIntent(SettingsIntent.LatencyModePicked(it)) }
            return@Row
        }
        if (ui.managingTrustedDevices) {
            BackHandler { onIntent(SettingsIntent.CloseTrustedDevices) }
            TrustedDevicesPane(
                devices = ui.trustedDevices,
                onForget = { onIntent(SettingsIntent.ForgetTrustedDevice(it)) },
                onClose = { onIntent(SettingsIntent.CloseTrustedDevices) },
            )
            return@Row
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Section(stringResource(R.string.section_general)) }
            item {
                ValueRow(
                    title = stringResource(R.string.setting_device_name),
                    value = s.deviceName,
                    onClick = { onIntent(SettingsIntent.PickDeviceName) },
                )
            }
            item {
                ToggleRow(stringResource(R.string.setting_start_on_boot), stringResource(R.string.setting_start_on_boot_desc), s.startOnBoot) {
                    onIntent(SettingsIntent.SetStartOnBoot(it))
                }
            }
            item {
                ToggleRow(stringResource(R.string.setting_background), stringResource(R.string.setting_background_desc), s.runInBackground) {
                    onIntent(SettingsIntent.SetRunInBackground(it))
                }
            }
            item {
                ToggleRow(stringResource(R.string.setting_open_on_connect), stringResource(R.string.setting_open_on_connect_desc), s.openAppOnConnect) {
                    onIntent(SettingsIntent.SetOpenAppOnConnect(it))
                }
            }
            item {
                ValueRow(
                    title = stringResource(R.string.setting_idle_dim),
                    value = idleDimLabel(s.idleDimMinutes),
                    onClick = { onIntent(SettingsIntent.PickIdleDim) },
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.setting_overlay),
                    value = stringResource(if (ui.overlayGranted) R.string.setting_overlay_granted else R.string.setting_overlay_missing),
                    onClick = { onIntent(SettingsIntent.GrantOverlay) },
                )
            }

            item { Section(stringResource(R.string.section_security)) }
            item {
                ToggleRow(stringResource(R.string.setting_require_pin), stringResource(R.string.setting_require_pin_desc), s.requirePin) {
                    onIntent(SettingsIntent.SetRequirePin(it))
                }
            }
            item {
                ToggleRow(stringResource(R.string.setting_remember_devices), stringResource(R.string.setting_remember_devices_desc), s.rememberDevices) {
                    onIntent(SettingsIntent.SetRememberDevices(it))
                }
            }
            item {
                ValueRow(
                    title = stringResource(R.string.setting_trusted_devices),
                    value = pluralStringResource(R.plurals.trusted_devices_count, ui.trustedDevices.size, ui.trustedDevices.size),
                    onClick = { onIntent(SettingsIntent.ManageTrustedDevices) },
                )
            }

            item { Section(stringResource(R.string.section_media)) }
            item {
                ToggleRow(stringResource(R.string.setting_video), stringResource(R.string.setting_video_desc), s.advertiseVideo) {
                    onIntent(SettingsIntent.SetAdvertiseVideo(it))
                }
            }
            item {
                ToggleRow(stringResource(R.string.setting_audio), stringResource(R.string.setting_audio_desc), s.advertiseAudio) {
                    onIntent(SettingsIntent.SetAdvertiseAudio(it))
                }
            }
            item {
                ToggleRow(stringResource(R.string.setting_hevc), stringResource(R.string.setting_hevc_desc), s.hevcEnabled) {
                    onIntent(SettingsIntent.SetHevcEnabled(it))
                }
            }
            item {
                ValueRow(
                    title = stringResource(R.string.setting_latency),
                    value = stringResource(latencyModeTitle(s.latencyMode)),
                    onClick = { onIntent(SettingsIntent.PickLatencyMode) },
                )
            }

            item { Section(stringResource(R.string.section_about)) }
            item {
                ValueRow(
                    title = stringResource(R.string.setting_diagnostics),
                    value = stringResource(R.string.setting_diagnostics_desc),
                    onClick = onOpenDiagnostics,
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.about_credit),
                    value = stringResource(R.string.about_credit_desc),
                    onClick = {},
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.setting_licenses),
                    value = stringResource(R.string.setting_licenses_desc),
                    onClick = onOpenLicenses,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 16.dp),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    ListItem(
        selected = false,
        onClick = { onChange(!checked) },
        interactionSource = interaction,
        headlineContent = { Text(title) },
        supportingContent = { SupportingText(description) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                // focused row is primary blue with dark content, so the switch flips to dark-on-blue too
                colors = if (focused) SwitchDefaults.colors(
                    checkedThumbColor = AirPlayColors.Primary,
                    checkedTrackColor = AirPlayColors.OnPrimary,
                    uncheckedThumbColor = AirPlayColors.OnPrimary,
                    uncheckedTrackColor = AirPlayColors.Primary,
                    uncheckedBorderColor = AirPlayColors.OnPrimary,
                ) else SwitchDefaults.colors(
                    checkedThumbColor = AirPlayColors.OnPrimary,
                    checkedTrackColor = AirPlayColors.Primary,
                    uncheckedThumbColor = AirPlayColors.Muted,
                    uncheckedTrackColor = AirPlayColors.Background,
                    uncheckedBorderColor = AirPlayColors.Muted,
                ),
            )
        },
        colors = appListItemColors(),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(title) },
        supportingContent = { SupportingText(value) },
        colors = appListItemColors(),
    )
}

@Composable
private fun idleDimLabel(minutes: Int): String =
    if (minutes <= 0) stringResource(R.string.idle_dim_never)
    else pluralStringResource(R.plurals.idle_dim_minutes, minutes, minutes)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun <T> OptionPicker(
    title: String,
    options: List<T>,
    current: T,
    label: @Composable (T) -> String,
    onPicked: (T?) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Section(title) }
        items(options) { option ->
            ListItem(
                selected = option == current,
                onClick = { onPicked(option) },
                headlineContent = { Text(label(option)) },
                trailingContent = { if (option == current) Icon(Icons.Default.Check, null) },
                colors = appListItemColors(),
            )
        }
        item {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onPicked(null) }) { Text(stringResource(R.string.action_back)) }
            }
        }
    }
}

private fun latencyModeTitle(mode: LatencyMode) = when (mode) {
    LatencyMode.LOW -> R.string.latency_low
    LatencyMode.BALANCED -> R.string.latency_balanced
    LatencyMode.SMOOTH -> R.string.latency_smooth
}

private fun latencyModeDesc(mode: LatencyMode) = when (mode) {
    LatencyMode.LOW -> R.string.latency_low_desc
    LatencyMode.BALANCED -> R.string.latency_balanced_desc
    LatencyMode.SMOOTH -> R.string.latency_smooth_desc
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LatencyModePicker(current: LatencyMode, onPicked: (LatencyMode?) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Section(stringResource(R.string.setting_latency)) }
        items(LatencyMode.entries) { mode ->
            ListItem(
                selected = mode == current,
                onClick = { onPicked(mode) },
                headlineContent = { Text(stringResource(latencyModeTitle(mode))) },
                supportingContent = { SupportingText(stringResource(latencyModeDesc(mode))) },
                trailingContent = { if (mode == current) Icon(Icons.Default.Check, null) },
                colors = appListItemColors(),
            )
        }
        item {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onPicked(null) }) { Text(stringResource(R.string.action_back)) }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrustedDevicesPane(devices: List<TrustedDevice>, onForget: (String?) -> Unit, onClose: () -> Unit) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Section(stringResource(R.string.setting_trusted_devices)) }
        if (devices.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.trusted_devices_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(devices, key = { it.id }) { d ->
            ListItem(
                selected = false,
                onClick = { onForget(d.id) },
                headlineContent = { Text(d.name) },
                supportingContent = {
                    SupportingText(stringResource(R.string.trusted_device_last_seen, dateFormat.format(Date(d.lastSeenAt))))
                },
                trailingContent = { SupportingText(stringResource(R.string.trusted_device_forget), fontSize = 12.sp) },
                colors = appListItemColors(),
            )
        }
        item {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (devices.isNotEmpty()) {
                    Button(onClick = { onForget(null) }) { Text(stringResource(R.string.trusted_devices_forget_all)) }
                    Spacer(Modifier.width(12.dp))
                }
                Button(onClick = onClose) { Text(stringResource(R.string.action_back)) }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DeviceNamePicker(current: String, onPicked: (String?) -> Unit) {
    val options = (listOf(current) + DEVICE_NAME_PRESETS).distinct()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Section(stringResource(R.string.setting_device_name)) }
        items(options) { name ->
            ListItem(
                selected = name == current,
                onClick = { onPicked(name) },
                headlineContent = { Text(name) },
                trailingContent = { if (name == current) Icon(Icons.Default.Check, null) },
                colors = appListItemColors(),
            )
        }
        item {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onPicked(null) }) { Text(stringResource(R.string.action_back)) }
            }
        }
    }
}
