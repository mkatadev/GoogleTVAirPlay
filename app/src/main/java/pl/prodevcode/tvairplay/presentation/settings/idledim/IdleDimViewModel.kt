package pl.prodevcode.tvairplay.presentation.settings.idledim

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import pl.prodevcode.tvairplay.domain.usecase.ObserveSettingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.UpdateSettingsUseCase
import pl.prodevcode.tvairplay.presentation.mvi.MviViewModel

@HiltViewModel
class IdleDimViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
) : MviViewModel<IdleDimUiState, IdleDimIntent, IdleDimEffect>(IdleDimUiState()) {

    init {
        observeSettings().reduceInto { copy(currentMinutes = it.idleDimMinutes) }
    }

    override fun onIntent(intent: IdleDimIntent) {
        when (intent) {
            is IdleDimIntent.Pick -> viewModelScope.launch {
                updateSettings { it.copy(idleDimMinutes = intent.minutes) }
                sendEffect(IdleDimEffect.Close)
            }
            IdleDimIntent.Back -> sendEffect(IdleDimEffect.Close)
        }
    }
}
