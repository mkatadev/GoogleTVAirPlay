package pl.prodevcode.tvairplay.presentation.settings.homekit

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import pl.prodevcode.tvairplay.domain.usecase.ObserveHomeKitStatusUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveSettingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.ResetHomeKitPairingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.UpdateSettingsUseCase
import pl.prodevcode.tvairplay.presentation.mvi.MviViewModel

@HiltViewModel
class HomeKitViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    observeHomeKitStatus: ObserveHomeKitStatusUseCase,
    private val resetPairings: ResetHomeKitPairingsUseCase,
) : MviViewModel<HomeKitUiState, HomeKitIntent, HomeKitEffect>(HomeKitUiState()) {

    init {
        observeSettings().reduceInto { copy(enabled = it.homeKitEnabled, deviceName = it.deviceName) }
        observeHomeKitStatus().reduceInto { copy(status = it) }
    }

    override fun onIntent(intent: HomeKitIntent) {
        when (intent) {
            is HomeKitIntent.SetEnabled -> viewModelScope.launch { updateSettings { it.copy(homeKitEnabled = intent.enabled) } }
            HomeKitIntent.ResetPairings -> setState { copy(confirmReset = true) }
            HomeKitIntent.ConfirmReset -> {
                setState { copy(confirmReset = false) }
                resetPairings()
            }
            HomeKitIntent.CancelReset -> setState { copy(confirmReset = false) }
            HomeKitIntent.OpenTvControl -> sendEffect(HomeKitEffect.OpenAccessibilitySettings)
            HomeKitIntent.Back -> sendEffect(HomeKitEffect.Close)
        }
    }
}
