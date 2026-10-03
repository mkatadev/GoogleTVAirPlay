package pl.prodevcode.tvairplay.presentation.settings.latency

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import pl.prodevcode.tvairplay.domain.usecase.ObserveSettingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.UpdateSettingsUseCase
import pl.prodevcode.tvairplay.presentation.mvi.MviViewModel

@HiltViewModel
class LatencyViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
) : MviViewModel<LatencyUiState, LatencyIntent, LatencyEffect>(LatencyUiState()) {

    init {
        observeSettings().reduceInto { copy(current = it.latencyMode) }
    }

    override fun onIntent(intent: LatencyIntent) {
        when (intent) {
            is LatencyIntent.Pick -> viewModelScope.launch {
                updateSettings { it.copy(latencyMode = intent.mode) }
                sendEffect(LatencyEffect.Close)
            }
            LatencyIntent.Back -> sendEffect(LatencyEffect.Close)
        }
    }
}
