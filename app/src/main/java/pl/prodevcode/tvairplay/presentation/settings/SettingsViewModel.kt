package pl.prodevcode.tvairplay.presentation.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import pl.prodevcode.tvairplay.domain.model.ReceiverSettings
import pl.prodevcode.tvairplay.domain.usecase.ObserveOverlayPermissionUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveSettingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.RequestOverlayPermissionUseCase
import pl.prodevcode.tvairplay.domain.usecase.RestartReceiverUseCase
import pl.prodevcode.tvairplay.domain.usecase.UpdateSettingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveTrustedDevicesUseCase
import pl.prodevcode.tvairplay.domain.usecase.ForgetTrustedDeviceUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveUpdateUseCase
import pl.prodevcode.tvairplay.domain.usecase.CheckForUpdateUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveInstallProgressUseCase
import pl.prodevcode.tvairplay.domain.usecase.InstallUpdateUseCase
import pl.prodevcode.tvairplay.presentation.mvi.MviViewModel
import pl.prodevcode.tvairplay.presentation.settings.SettingsIntent as Intent

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val restartReceiver: RestartReceiverUseCase,
    private val observeOverlay: ObserveOverlayPermissionUseCase,
    private val requestOverlay: RequestOverlayPermissionUseCase,
    observeTrustedDevices: ObserveTrustedDevicesUseCase,
    private val forgetTrustedDevice: ForgetTrustedDeviceUseCase,
    observeUpdate: ObserveUpdateUseCase,
    private val checkForUpdate: CheckForUpdateUseCase,
    observeInstallProgress: ObserveInstallProgressUseCase,
    private val installUpdate: InstallUpdateUseCase,
) : MviViewModel<SettingsUiState, Intent, SettingsEffect>(SettingsUiState()) {

    private var restartJob: Job? = null

    init {
        observeSettings().reduceInto { copy(settings = it) }
        observeOverlay().reduceInto { copy(overlayGranted = it) }
        observeTrustedDevices().reduceInto { copy(trustedDevices = it) }
        observeUpdate().reduceInto { copy(update = it) }
        observeInstallProgress().reduceInto { copy(install = it) }
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            Intent.ScreenResumed -> {
                observeOverlay.refresh()
                viewModelScope.launch { checkForUpdate() }
            }
            Intent.CheckForUpdate -> viewModelScope.launch { checkForUpdate(force = true) }
            Intent.InstallUpdate -> viewModelScope.launch { installUpdate() }
            Intent.GrantOverlay -> requestOverlay()
            Intent.PickDeviceName -> setState { copy(pickingDeviceName = true) }
            is Intent.DeviceNamePicked -> {
                setState { copy(pickingDeviceName = false) }
                intent.name?.let { n -> update(restart = true) { copy(deviceName = n) } }
            }
            is Intent.SetStartOnBoot -> update { copy(startOnBoot = intent.enabled) }
            is Intent.SetRunInBackground -> update { copy(runInBackground = intent.enabled) }
            is Intent.SetOpenAppOnConnect -> update { copy(openAppOnConnect = intent.enabled) }
            is Intent.SetRequirePin -> update(restart = true) { copy(requirePin = intent.enabled) }
            is Intent.SetRememberDevices -> update { copy(rememberDevices = intent.enabled) }
            Intent.ManageTrustedDevices -> setState { copy(managingTrustedDevices = true) }
            Intent.CloseTrustedDevices -> setState { copy(managingTrustedDevices = false) }
            is Intent.ForgetTrustedDevice -> forgetTrustedDevice(intent.id)
            is Intent.SetAdvertiseVideo -> update(restart = true) { copy(advertiseVideo = intent.enabled) }
            is Intent.SetAdvertiseAudio -> update(restart = true) { copy(advertiseAudio = intent.enabled) }
            is Intent.SetHevcEnabled -> update(restart = true) { copy(hevcEnabled = intent.enabled) }
            Intent.PickIdleDim -> setState { copy(pickingIdleDim = true) }
            is Intent.IdleDimPicked -> {
                setState { copy(pickingIdleDim = false) }
                intent.minutes?.let { m -> update { copy(idleDimMinutes = m) } }
            }
            Intent.PickLatencyMode -> setState { copy(pickingLatencyMode = true) }
            is Intent.LatencyModePicked -> {
                setState { copy(pickingLatencyMode = false) }
                intent.mode?.let { m -> update { copy(latencyMode = m) } }
            }
        }
    }

    /** Settings advertised over mDNS only take effect after the receiver restarts. */
    private fun update(restart: Boolean = false, transform: ReceiverSettings.() -> ReceiverSettings) {
        viewModelScope.launch {
            updateSettings(transform)
            if (restart) scheduleRestart()
        }
    }

    private fun scheduleRestart() {
        restartJob?.cancel()
        restartJob = viewModelScope.launch {
            delay(RESTART_DEBOUNCE_MS)
            restartReceiver()
        }
    }

    private companion object {
        const val RESTART_DEBOUNCE_MS = 1_200L
    }
}
