package pl.prodevcode.tvairplay.presentation.settings.language

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.prodevcode.tvairplay.R
import pl.prodevcode.tvairplay.domain.model.AppLanguage
import pl.prodevcode.tvairplay.presentation.components.OptionRow
import pl.prodevcode.tvairplay.presentation.components.SettingsPage
import pl.prodevcode.tvairplay.presentation.mvi.CollectEffects
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import pl.prodevcode.tvairplay.presentation.theme.TvAirPlayTheme

@Composable
fun LanguageScreen(onBack: () -> Unit, viewModel: LanguageViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { if (it == LanguageEffect.Close) onBack() }
    LanguageContent(ui, viewModel::onIntent)
}

@Composable
private fun LanguageContent(ui: LanguageUiState, onIntent: (LanguageIntent) -> Unit) {
    SettingsPage(
        title = stringResource(R.string.setting_language),
        subtitle = stringResource(R.string.language_subtitle),
        onBack = { onIntent(LanguageIntent.Back) },
    ) {
        LazyColumn(
            Modifier.weight(1f).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(AppLanguage.entries) { language ->
                OptionRow(
                    title = languageLabel(language),
                    selected = language == ui.current,
                    onClick = { onIntent(LanguageIntent.Pick(language)) },
                )
            }
        }
    }
}

@Composable
fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(R.string.language_system)
    // native names on purpose: a user stuck in the wrong language must still recognise their own
    AppLanguage.ENGLISH -> "English"
    AppLanguage.POLISH -> "Polski"
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun LanguagePreview() {
    TvAirPlayTheme { LanguageContent(LanguageUiState(current = AppLanguage.POLISH), onIntent = {}) }
}
