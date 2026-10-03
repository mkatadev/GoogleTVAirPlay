package pl.prodevcode.tvairplay.presentation.settings.language

import pl.prodevcode.tvairplay.domain.model.AppLanguage
import pl.prodevcode.tvairplay.presentation.mvi.UiEffect
import pl.prodevcode.tvairplay.presentation.mvi.UiIntent
import pl.prodevcode.tvairplay.presentation.mvi.UiState

data class LanguageUiState(val current: AppLanguage = AppLanguage.SYSTEM) : UiState

sealed interface LanguageIntent : UiIntent {
    data class Pick(val language: AppLanguage) : LanguageIntent
    data object Back : LanguageIntent
}

sealed interface LanguageEffect : UiEffect {
    data object Close : LanguageEffect
}
