package pl.prodevcode.tvairplay.presentation.licenses

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
import pl.prodevcode.tvairplay.presentation.components.SupportingText
import pl.prodevcode.tvairplay.presentation.mvi.CollectEffects
import pl.prodevcode.tvairplay.presentation.components.appListItemColors
import pl.prodevcode.tvairplay.presentation.theme.AirPlayColors

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    viewModel: LicensesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { if (it == LicensesEffect.NavigateBack) onBack() }
    LicensesContent(state = state, onIntent = viewModel::onIntent)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LicensesContent(state: LicensesUiState, onIntent: (LicensesIntent) -> Unit) {
    BackHandler { onIntent(LicensesIntent.Back) }

    Row(Modifier.fillMaxSize().background(AirPlayColors.Background)) {
        Column(Modifier.width(360.dp).fillMaxSize().background(AirPlayColors.Surface).padding(40.dp)) {
            Text(stringResource(R.string.licenses_title), fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.licenses_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.about_credit), color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Button(onClick = { onIntent(LicensesIntent.Back) }) { Text(stringResource(R.string.action_back)) }
        }

        val selected = state.selected
        if (selected != null) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 40.dp)
                    .verticalScroll(rememberScrollState())
                    .focusable(),
            ) {
                Text(selected.name, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("${selected.license} · ${selected.url}", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                Spacer(Modifier.height(24.dp))
                Text(
                    state.licenseText.ifBlank { "…" },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.components) { c ->
                    ListItem(
                        selected = false,
                        onClick = { onIntent(LicensesIntent.Select(c)) },
                        headlineContent = { Text(c.name) },
                        supportingContent = { SupportingText(c.license) },
                        trailingContent = { SupportingText(c.url, fontSize = 12.sp) },
                        colors = appListItemColors(),
                    )
                }
            }
        }
    }
}
