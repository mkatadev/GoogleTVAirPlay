package pl.prodevcode.tvairplay.presentation.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import pl.prodevcode.tvairplay.R
import pl.prodevcode.tvairplay.domain.model.AdvertisingState
import pl.prodevcode.tvairplay.domain.model.CheckResult
import pl.prodevcode.tvairplay.domain.model.DiagnosticCheck
import pl.prodevcode.tvairplay.domain.model.Diagnostics
import pl.prodevcode.tvairplay.domain.model.ReceiverStatus
import pl.prodevcode.tvairplay.domain.model.SessionMode
import pl.prodevcode.tvairplay.presentation.components.SupportingText
import pl.prodevcode.tvairplay.presentation.components.appListItemColors
import pl.prodevcode.tvairplay.presentation.components.localizedLog
import pl.prodevcode.tvairplay.presentation.theme.AirPlayColors

@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DiagnosticsContent(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DiagnosticsContent(
    state: DiagnosticsUiState,
    onIntent: (DiagnosticsIntent) -> Unit,
    onBack: () -> Unit,
) {
    val d = state.diagnostics
    Row(Modifier.fillMaxSize().background(AirPlayColors.Background)) {
        Column(Modifier.width(360.dp).fillMaxSize().background(AirPlayColors.Surface).padding(40.dp)) {
            Text(stringResource(R.string.diagnostics_title), fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.diagnostics_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.diagnostics_version, d.appVersion), color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Button(onClick = { onIntent(DiagnosticsIntent.RestartReceiver) }) { Text(stringResource(R.string.diagnostics_restart)) }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onBack) { Text(stringResource(R.string.action_back)) }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Section(stringResource(R.string.diagnostics_section_checks)) }
            item {
                CheckRow(
                    result = state.checks[DiagnosticCheck.RECEIVER],
                    title = stringResource(R.string.diagnostics_check_receiver),
                    detail = receiverDetail(d),
                )
            }
            item {
                CheckRow(
                    result = state.checks[DiagnosticCheck.NETWORK],
                    title = stringResource(R.string.diagnostics_check_network),
                    detail = networkDetail(d),
                )
            }
            item {
                CheckRow(
                    result = state.checks[DiagnosticCheck.ADVERTISING],
                    title = stringResource(R.string.diagnostics_check_advertising),
                    detail = advertisingDetail(d),
                )
            }
            item {
                CheckRow(
                    result = state.checks[DiagnosticCheck.PORT],
                    title = stringResource(R.string.diagnostics_check_port),
                    detail = portDetail(d),
                )
            }

            item { Section(stringResource(R.string.diagnostics_section_session)) }
            item {
                InfoRow(
                    stringResource(R.string.diagnostics_clients),
                    "${d.session.connectedClients} · ${sessionModeLabel(d.session.mode)}",
                )
            }
            if (d.session.mode == SessionMode.MIRRORING || d.session.mode == SessionMode.VIDEO) {
                item {
                    InfoRow(
                        stringResource(R.string.diagnostics_video),
                        listOf(d.session.videoCodec, d.session.videoResolution, "${d.session.videoFps} fps",
                            pluralStringResource(R.plurals.diagnostics_dropped, d.session.droppedFrames.toInt(), d.session.droppedFrames.toInt()))
                            .filter { it.isNotBlank() }.joinToString(" · "),
                    )
                }
            }
            if (d.session.mode == SessionMode.AUDIO || d.session.mode == SessionMode.MIRRORING) {
                item {
                    InfoRow(
                        stringResource(R.string.diagnostics_audio),
                        listOf(d.session.audioCodec, pluralStringResource(R.plurals.diagnostics_underruns, d.session.audioUnderruns, d.session.audioUnderruns))
                            .filter { it.isNotBlank() }.joinToString(" · "),
                    )
                }
            }
            item { InfoRow(stringResource(R.string.diagnostics_last_event), if (d.lastLog.isBlank()) "—" else localizedLog(d.lastLog)) }
        }
    }
}

@Composable
private fun receiverDetail(d: Diagnostics): String = when (d.receiverStatus) {
    ReceiverStatus.RUNNING -> stringResource(R.string.diagnostics_receiver_running, d.deviceName)
    ReceiverStatus.STARTING -> stringResource(R.string.diagnostics_receiver_starting)
    ReceiverStatus.STOPPED -> stringResource(R.string.diagnostics_receiver_stopped)
    ReceiverStatus.ERROR -> d.lastError ?: stringResource(R.string.status_error)
}

@Composable
private fun networkDetail(d: Diagnostics): String = when {
    d.network.addresses.isEmpty() -> stringResource(R.string.diagnostics_network_none)
    d.network.addresses.any { it.startsWith("169.254.") } -> stringResource(R.string.diagnostics_network_link_local)
    else -> buildString {
        append(d.network.transport.ifBlank { d.network.interfaceName })
        append(" · ")
        append(d.network.addresses.joinToString())
        if (d.network.changes > 0) append(" · ").append(stringResource(R.string.diagnostics_network_changes, d.network.changes))
    }
}

@Composable
private fun advertisingDetail(d: Diagnostics): String {
    val a = d.advertising
    if (d.receiverStatus != ReceiverStatus.RUNNING) return stringResource(R.string.diagnostics_advertising_off)
    val base = "_airplay: ${advertisingLabel(a.airplay, a.airplayError)} · _raop: ${advertisingLabel(a.raop, a.raopError)}"
    return if (a.airplay == AdvertisingState.FAILED || a.raop == AdvertisingState.FAILED)
        base + "\n" + stringResource(R.string.diagnostics_adv_hint) else base
}

@Composable
private fun advertisingLabel(s: AdvertisingState, err: Int): String = when (s) {
    AdvertisingState.REGISTERED -> stringResource(R.string.diagnostics_adv_ok)
    AdvertisingState.PENDING -> stringResource(R.string.diagnostics_adv_pending)
    AdvertisingState.FAILED -> stringResource(R.string.diagnostics_adv_failed, err)
    AdvertisingState.IDLE -> "—"
}

@Composable
private fun portDetail(d: Diagnostics): String = when {
    d.receiverStatus == ReceiverStatus.RUNNING && d.port > 0 -> stringResource(R.string.diagnostics_port_listening, d.port)
    d.receiverStatus == ReceiverStatus.ERROR && d.lastError?.contains("port", true) == true -> stringResource(R.string.diagnostics_port_busy)
    else -> "—"
}

@Composable
private fun sessionModeLabel(mode: SessionMode): String = when (mode) {
    SessionMode.IDLE -> stringResource(R.string.diagnostics_mode_idle)
    SessionMode.CONNECTED -> stringResource(R.string.diagnostics_mode_connected)
    SessionMode.MIRRORING -> stringResource(R.string.diagnostics_mode_mirroring)
    SessionMode.VIDEO -> stringResource(R.string.diagnostics_mode_video)
    SessionMode.AUDIO -> stringResource(R.string.diagnostics_mode_audio)
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
private fun CheckRow(result: CheckResult?, title: String, detail: String) {
    val color = when (result) {
        CheckResult.OK -> Color(0xFF3DDC84)
        CheckResult.WARNING -> Color(0xFFFFC107)
        CheckResult.ERROR -> Color(0xFFFF5252)
        CheckResult.UNKNOWN, null -> AirPlayColors.Muted
    }
    ListItem(
        selected = false,
        onClick = {},
        leadingContent = { Box(Modifier.size(14.dp).clip(CircleShape).background(color)) },
        headlineContent = { Text(title) },
        supportingContent = { SupportingText(detail) },
        colors = appListItemColors(),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun InfoRow(title: String, value: String) {
    ListItem(
        selected = false,
        onClick = {},
        headlineContent = { Text(title) },
        supportingContent = { SupportingText(value) },
        colors = appListItemColors(),
    )
}
