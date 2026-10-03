package pl.prodevcode.tvairplay.presentation.settings.language

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import pl.prodevcode.tvairplay.domain.usecase.ObserveSettingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.UpdateSettingsUseCase
import pl.prodevcode.tvairplay.presentation.mvi.MviViewModel

@HiltViewModel
class LanguageViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
) : MviViewModel<LanguageUiState, LanguageIntent, LanguageEffect>(LanguageUiState()) {

    init {
        observeSettings().reduceInto { copy(current = it.language) }
    }

    override fun onIntent(intent: LanguageIntent) {
        when (intent) {
            is LanguageIntent.Pick -> viewModelScope.launch {
                updateSettings { it.copy(language = intent.language) }
                sendEffect(LanguageEffect.Close)
            }
            LanguageIntent.Back -> sendEffect(LanguageEffect.Close)
        }
    }
}
