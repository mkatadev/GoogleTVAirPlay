package pl.prodevcode.tvairplay.presentation.settings.trusted

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import pl.prodevcode.tvairplay.domain.usecase.ForgetTrustedDeviceUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveTrustedDevicesUseCase
import pl.prodevcode.tvairplay.presentation.mvi.MviViewModel

@HiltViewModel
class TrustedDevicesViewModel @Inject constructor(
    observeTrustedDevices: ObserveTrustedDevicesUseCase,
    private val forgetTrustedDevice: ForgetTrustedDeviceUseCase,
) : MviViewModel<TrustedDevicesUiState, TrustedDevicesIntent, TrustedDevicesEffect>(TrustedDevicesUiState()) {

    init {
        observeTrustedDevices().reduceInto { copy(devices = it) }
    }

    override fun onIntent(intent: TrustedDevicesIntent) {
        when (intent) {
            is TrustedDevicesIntent.Forget -> forgetTrustedDevice(intent.id)
            TrustedDevicesIntent.Back -> sendEffect(TrustedDevicesEffect.Close)
        }
    }
}
