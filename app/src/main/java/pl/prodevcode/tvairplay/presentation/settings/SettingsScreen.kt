package pl.prodevcode.tvairplay.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.prodevcode.tvairplay.R
import pl.prodevcode.tvairplay.domain.model.InstallFailure
import pl.prodevcode.tvairplay.domain.model.InstallProgress
import pl.prodevcode.tvairplay.domain.model.UpdateCheck
import pl.prodevcode.tvairplay.presentation.components.SectionHeader
import pl.prodevcode.tvairplay.presentation.components.SettingsPage
import pl.prodevcode.tvairplay.presentation.components.ToggleRow
import pl.prodevcode.tvairplay.presentation.components.ValueRow
import pl.prodevcode.tvairplay.presentation.mvi.CollectEffects
import pl.prodevcode.tvairplay.presentation.settings.homekit.homeKitSummary
import pl.prodevcode.tvairplay.presentation.settings.idledim.idleDimLabel
import pl.prodevcode.tvairplay.presentation.settings.language.languageLabel
import pl.prodevcode.tvairplay.presentation.settings.latency.latencyModeTitle
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import pl.prodevcode.tvairplay.presentation.theme.TvAirPlayTheme
import pl.prodevcode.tvairplay.domain.model.AppUpdate

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpen: (SettingsSub) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { if (it is SettingsEffect.Navigate) onOpen(it.sub) }
    SettingsContent(ui = ui, onIntent = viewModel::onIntent, onBack = onBack)
}

@Composable
private fun SettingsContent(ui: SettingsUiState, onIntent: (SettingsIntent) -> Unit, onBack: () -> Unit) {
    val s = ui.settings
    LifecycleResumeEffect(Unit) { onIntent(SettingsIntent.ScreenResumed); onPauseOrDispose { } }

    @Composable
    fun SubRow(sub: SettingsSub, title: String, value: String) =
        ValueRow(title, value, onClick = { onIntent(SettingsIntent.Open(sub)) }, focusOnEntry = ui.lastOpened == sub)

    SettingsPage(
        title = stringResource(R.string.action_settings),
        subtitle = stringResource(R.string.settings_subtitle),
        onBack = onBack,
    ) {
        LazyColumn(
            Modifier.weight(1f).fillMaxSize(),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionHeader(stringResource(R.string.section_general)) }
            item { SubRow(SettingsSub.DEVICE_NAME, stringResource(R.string.setting_device_name), s.deviceName) }
            item { SubRow(SettingsSub.LANGUAGE, stringResource(R.string.setting_language), languageLabel(s.language)) }
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
            item { SubRow(SettingsSub.IDLE_DIM, stringResource(R.string.setting_idle_dim), idleDimLabel(s.idleDimMinutes)) }
            item {
                ValueRow(
                    title = stringResource(R.string.setting_overlay),
                    value = stringResource(if (ui.overlayGranted) R.string.setting_overlay_granted else R.string.setting_overlay_missing),
                    onClick = { onIntent(SettingsIntent.GrantOverlay) },
                )
            }

            item { SectionHeader(stringResource(R.string.section_security)) }
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
                SubRow(
                    SettingsSub.TRUSTED_DEVICES,
                    stringResource(R.string.setting_trusted_devices),
                    pluralStringResource(R.plurals.trusted_devices_count, ui.trustedDeviceCount, ui.trustedDeviceCount),
                )
            }

            item { SectionHeader(stringResource(R.string.section_media)) }
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
                ToggleRow(stringResource(R.string.setting_subtitles), stringResource(R.string.setting_subtitles_desc), s.subtitlesByDefault) {
                    onIntent(SettingsIntent.SetSubtitlesByDefault(it))
                }
            }
            item { SubRow(SettingsSub.LATENCY, stringResource(R.string.setting_latency), stringResource(latencyModeTitle(s.latencyMode))) }

            item { SectionHeader(stringResource(R.string.section_smart_home)) }
            item { SubRow(SettingsSub.HOMEKIT, stringResource(R.string.setting_homekit), homeKitSummary(s.homeKitEnabled, ui.homeKit)) }

            item { SectionHeader(stringResource(R.string.section_about)) }
            item {
                val installable = (ui.update as? UpdateCheck.Available)?.update?.takeIf { it.installable }
                val busy = ui.install is InstallProgress.Downloading || ui.install == InstallProgress.Verifying
                ValueRow(
                    title = stringResource(R.string.setting_update),
                    value = installable?.let { installLabel(ui.install, it.latestVersion) } ?: updateLabel(ui.update),
                    onClick = {
                        if (installable != null && !busy) onIntent(SettingsIntent.InstallUpdate)
                        else onIntent(SettingsIntent.CheckForUpdate)
                    },
                )
            }
            item { SubRow(SettingsSub.DIAGNOSTICS, stringResource(R.string.setting_diagnostics), stringResource(R.string.setting_diagnostics_desc)) }
            item { SubRow(SettingsSub.LICENSES, stringResource(R.string.setting_licenses), stringResource(R.string.setting_licenses_desc)) }
        }
    }
}

@Composable
private fun installLabel(progress: InstallProgress, version: String): String = when (progress) {
    InstallProgress.Idle -> stringResource(R.string.update_install, version)
    is InstallProgress.Downloading -> stringResource(R.string.update_downloading, version, progress.percent)
    InstallProgress.Verifying -> stringResource(R.string.update_verifying)
    InstallProgress.AwaitingConfirmation -> stringResource(R.string.update_confirm)
    InstallProgress.NeedsPermission -> stringResource(R.string.update_needs_permission)
    is InstallProgress.Failed -> stringResource(
        when (progress.reason) {
            InstallFailure.DOWNLOAD -> R.string.update_failed_download
            InstallFailure.CHECKSUM -> R.string.update_failed_checksum
            InstallFailure.INSTALLER -> R.string.update_failed_installer
            InstallFailure.ABORTED -> R.string.update_failed_aborted
        }
    )
}

@Composable
private fun updateLabel(check: UpdateCheck): String = when (check) {
    UpdateCheck.Idle, UpdateCheck.Checking -> stringResource(R.string.update_checking)
    is UpdateCheck.UpToDate -> stringResource(R.string.update_up_to_date, check.currentVersion)
    is UpdateCheck.Available -> stringResource(R.string.update_available, check.update.latestVersion, check.update.currentVersion)
    is UpdateCheck.Failed -> stringResource(R.string.update_failed, check.currentVersion)
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun SettingsPreview() {
    TvAirPlayTheme {
        SettingsContent(
            ui = SettingsUiState(
                trustedDeviceCount = 2,
                update = UpdateCheck.Available(AppUpdate("1.0.0", "1.1.0", "https://example.invalid")),
                lastOpened = SettingsSub.LANGUAGE,
            ),
            onIntent = {}, onBack = {},
        )
    }
}
