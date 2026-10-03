package pl.prodevcode.tvairplay.presentation.settings.devicename

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import pl.prodevcode.tvairplay.domain.usecase.ObserveSettingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.RestartReceiverUseCase
import pl.prodevcode.tvairplay.domain.usecase.UpdateSettingsUseCase
import pl.prodevcode.tvairplay.presentation.mvi.MviViewModel

@HiltViewModel
class DeviceNameViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val restartReceiver: RestartReceiverUseCase,
) : MviViewModel<DeviceNameUiState, DeviceNameIntent, DeviceNameEffect>(DeviceNameUiState()) {

    init {
        observeSettings().reduceInto { copy(current = it.deviceName) }
    }

    override fun onIntent(intent: DeviceNameIntent) {
        when (intent) {
            is DeviceNameIntent.DraftChanged -> setState { copy(draft = intent.text.take(DEVICE_NAME_MAX)) }
            DeviceNameIntent.Save -> if (currentState.canSave) save(currentState.text.trim())
            is DeviceNameIntent.Pick -> save(intent.name)
            DeviceNameIntent.Back -> sendEffect(DeviceNameEffect.Close)
        }
    }

    /** The name is advertised over mDNS, so the receiver restarts to re-announce. */
    private fun save(name: String) {
        viewModelScope.launch {
            if (name != currentState.current) {
                updateSettings { it.copy(deviceName = name) }
                restartReceiver()
            }
            sendEffect(DeviceNameEffect.Close)
        }
    }
}
