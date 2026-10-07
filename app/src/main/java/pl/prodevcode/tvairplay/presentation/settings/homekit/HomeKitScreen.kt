package pl.prodevcode.tvairplay.presentation.settings.homekit

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import pl.prodevcode.tvairplay.presentation.theme.AirPlayColors
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import pl.prodevcode.airplay.R as CoreR
import pl.prodevcode.tvairplay.R
import pl.prodevcode.tvairplay.domain.model.HomeKitStatus
import pl.prodevcode.tvairplay.presentation.components.QrCode
import pl.prodevcode.tvairplay.presentation.components.SectionHeader
import pl.prodevcode.tvairplay.presentation.components.SettingsPage
import pl.prodevcode.tvairplay.presentation.components.ToggleRow
import pl.prodevcode.tvairplay.presentation.components.ValueRow
import pl.prodevcode.tvairplay.presentation.mvi.CollectEffects
import pl.prodevcode.tvairplay.presentation.theme.TvAirPlayTheme

@Composable
fun HomeKitScreen(onBack: () -> Unit, viewModel: HomeKitViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    CollectEffects(viewModel.effects) {
        when (it) {
            HomeKitEffect.Close -> onBack()
            HomeKitEffect.OpenAccessibilitySettings ->
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
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
            item { InfoRow(stringResource(R.string.setting_homekit), homeKitSummary(ui.enabled, status)) }
            if (ui.enabled) {
                item {
                    ValueRow(
                        stringResource(R.string.homekit_tv_control),
                        if (status.tvControl) stringResource(R.string.homekit_tv_control_on)
                        else stringResource(R.string.homekit_tv_control_off, stringResource(CoreR.string.tv_remote_service_summary)),
                        onClick = { onIntent(HomeKitIntent.OpenTvControl) },
                    )
                }
            }

            val code = status.setupCode
            if (ui.enabled && code != null) {
                item { SectionHeader(stringResource(R.string.homekit_setup_code)) }
                item {
                    // focusable so the D-pad can scroll the list down to the code and its instructions
                    FocusableBlock {
                    Column(Modifier.fillMaxWidth().padding(24.dp)) {
                        status.setupUri?.let { uri ->
                            QrCode(uri, contentDescription = stringResource(R.string.homekit_qr_description), size = 240.dp)
                            Spacer(Modifier.height(16.dp))
                        }
                        Text(
                            homeKitCodeLabel(code),
                            fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                            maxLines = 1, softWrap = false,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.homekit_setup_qr_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.homekit_setup_code_hint, ui.deviceName), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                    }
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
                        val cancelFocus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { cancelFocus.requestFocus() }
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(onClick = { onIntent(HomeKitIntent.CancelReset) }, modifier = Modifier.focusRequester(cancelFocus)) {
                                Text(stringResource(R.string.action_cancel))
                            }
                            Spacer(Modifier.width(16.dp))
                            Button(onClick = { onIntent(HomeKitIntent.ConfirmReset) }) { Text(stringResource(R.string.homekit_reset_confirm)) }
                        }
                    }
                }
            }

            if (status.accessoryId.isNotEmpty()) {
                item { InfoRow(stringResource(R.string.homekit_accessory_id), status.accessoryId) }
            }
        }
    }
}

/**
 * Non-interactive content that still takes D-pad focus: a LazyColumn can only move focus between
 * composed items, so every row on this screen must be focusable or the remote gets stuck.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FocusableBlock(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AirPlayColors.Surface, focusedContainerColor = AirPlayColors.Surface,
            contentColor = MaterialTheme.colorScheme.onSurface, focusedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, AirPlayColors.Primary), shape = shape)),
        content = { content() },
    )
}

/** Read-only line in the same rhythm as the list rows. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun InfoRow(title: String, value: String) {
    FocusableBlock {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(title, fontSize = 18.sp)
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }
}

/** HAP keeps the code as `XXX-XX-XXX`; the Home app shows and asks for it as `XXXX-XXXX`, so match that on screen. */
fun homeKitCodeLabel(setupCode: String): String {
    val digits = setupCode.filter { it.isDigit() }
    return if (digits.length == 8) "${digits.substring(0, 4)}-${digits.substring(4)}" else setupCode
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
                status = HomeKitStatus(running = true, setupCode = "031-45-154", setupUri = "X-HM://00FA5V57Y7OSX", accessoryId = "1A:2B:3C:4D:5E:6F"),
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
                status = HomeKitStatus(running = true, paired = true, controllers = 2, accessoryId = "1A:2B:3C:4D:5E:6F", tvControl = true),
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
