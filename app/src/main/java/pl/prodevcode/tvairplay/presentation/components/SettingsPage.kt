package pl.prodevcode.tvairplay.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import pl.prodevcode.tvairplay.R
import pl.prodevcode.tvairplay.presentation.theme.AirPlayColors

/** Two-pane settings layout: title, subtitle and Back on the left, [content] on the right. Back key calls [onBack]. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsPage(title: String, subtitle: String, onBack: () -> Unit, content: @Composable RowScope.() -> Unit) {
    BackHandler(onBack = onBack)
    Row(Modifier.fillMaxSize().background(AirPlayColors.Background)) {
        Column(Modifier.width(360.dp).fillMaxSize().background(AirPlayColors.Surface).padding(40.dp)) {
            Text(title, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, lineHeight = 38.sp)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Button(onClick = onBack) { Text(stringResource(R.string.action_back)) }
        }
        content()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 16.dp),
    )
}

/** Row that opens something; [focusOnEntry] grabs focus once when the row appears (return from a sub-screen). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ValueRow(title: String, value: String, onClick: () -> Unit, focusOnEntry: Boolean = false) {
    val focus = remember { FocusRequester() }
    if (focusOnEntry) LaunchedEffect(Unit) { focus.requestFocus() }
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(title) },
        supportingContent = { SupportingText(value) },
        colors = appListItemColors(),
        modifier = if (focusOnEntry) Modifier.focusRequester(focus) else Modifier,
    )
}

/** Selectable option with a check mark; the current one takes focus when the list appears. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OptionRow(title: String, selected: Boolean, onClick: () -> Unit, description: String? = null) {
    val focus = remember { FocusRequester() }
    if (selected) LaunchedEffect(Unit) { focus.requestFocus() }
    ListItem(
        selected = selected,
        onClick = onClick,
        headlineContent = { Text(title) },
        supportingContent = description?.let { { SupportingText(it) } },
        trailingContent = { if (selected) Icon(Icons.Default.Check, null) },
        colors = appListItemColors(),
        modifier = if (selected) Modifier.focusRequester(focus) else Modifier,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ToggleRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
