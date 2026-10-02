package pl.prodevcode.tvairplay.presentation.receiver.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import pl.prodevcode.tvairplay.R
import pl.prodevcode.tvairplay.domain.model.DeviceInfo
import pl.prodevcode.tvairplay.domain.model.ReceiverState
import pl.prodevcode.tvairplay.domain.model.ReceiverStatus
import pl.prodevcode.tvairplay.presentation.theme.AirPlayColors

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun IdleScreen(
    state: ReceiverState,
    device: DeviceInfo,
    onToggleReceiver: () -> Unit,
    onOpenSettings: () -> Unit,
    overlayPermissionGranted: Boolean = true,
    onGrantOverlay: () -> Unit = {},
) {
    val running = state.status == ReceiverStatus.RUNNING
    val connected = state.connectedClients > 0

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(AirPlayColors.SurfaceVariant, AirPlayColors.Background),
                    radius = 1400f,
                )
            )
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PulsingBadge(active = running, connected = connected)
            Spacer(Modifier.height(32.dp))
            Text(
                text = device.name.ifBlank { stringResource(R.string.app_name) },
                fontSize = 44.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = when {
                    state.status == ReceiverStatus.ERROR -> stringResource(R.string.status_error)
                    !running -> stringResource(R.string.status_stopped)
                    connected -> stringResource(R.string.status_connected)
                    else -> stringResource(R.string.status_ready)
                },
                fontSize = 20.sp,
                color = when {
                    state.status == ReceiverStatus.ERROR -> MaterialTheme.colorScheme.error
                    connected -> AirPlayColors.Accent
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(40.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepChip("1", stringResource(R.string.hint_step_1))
                Spacer(Modifier.width(16.dp))
                StepChip("2", stringResource(R.string.hint_step_2))
                Spacer(Modifier.width(16.dp))
                StepChip("3", stringResource(R.string.hint_step_3, device.name))
            }
            Spacer(Modifier.height(48.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onToggleReceiver,
                    colors = ButtonDefaults.colors(
                        containerColor = if (running) AirPlayColors.SurfaceVariant else MaterialTheme.colorScheme.primary,
                        contentColor = if (running) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (running) R.string.action_stop else R.string.action_start))
                }
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.colors(
                        containerColor = AirPlayColors.SurfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_settings))
                }
            }
        }

        if (!overlayPermissionGranted) {
            PermissionBanner(onGrantOverlay, Modifier.align(Alignment.TopCenter).padding(top = 28.dp))
        }

        Text(
            text = device.ipAddress?.let { stringResource(R.string.footer_network, it) }
                ?: stringResource(R.string.footer_no_network),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomStart).padding(40.dp),
        )
        Text(
            text = stringResource(R.string.footer_credit),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        )
        if (state.lastLog.isNotBlank()) {
            Text(
                text = state.lastLog,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(40.dp),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PermissionBanner(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AirPlayColors.Error.copy(alpha = 0.16f))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(Icons.Default.Warning, null, tint = AirPlayColors.Error)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(R.string.overlay_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.overlay_desc), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(20.dp))
        Button(
            onClick = onGrant,
            colors = ButtonDefaults.colors(containerColor = AirPlayColors.Error, contentColor = AirPlayColors.Background),
        ) { Text(stringResource(R.string.overlay_action)) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PulsingBadge(active: Boolean, connected: Boolean) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "scale",
    )
    val ringColor = when {
        connected -> AirPlayColors.Accent
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
        if (active) {
            Box(
                Modifier
                    .size(140.dp)
                    .scale(scale)
                    .alpha(0.18f)
                    .background(ringColor, CircleShape)
            )
        }
        Box(
            Modifier
                .size(120.dp)
                .background(AirPlayColors.Surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (connected) Icons.Default.CastConnected else Icons.Default.Cast,
                contentDescription = null,
                tint = ringColor,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StepChip(number: String, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(AirPlayColors.Surface.copy(alpha = 0.8f))
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Box(
            Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 16.sp, color = Color.White.copy(alpha = 0.9f))
    }
}
