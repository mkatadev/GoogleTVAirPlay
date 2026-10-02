package pl.prodevcode.tvairplay.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.prodevcode.tvairplay.domain.model.ReceiverSettings
import pl.prodevcode.tvairplay.domain.usecase.ObserveOverlayPermissionUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveSettingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.RequestOverlayPermissionUseCase
import pl.prodevcode.tvairplay.domain.usecase.RestartReceiverUseCase
import pl.prodevcode.tvairplay.domain.usecase.UpdateSettingsUseCase

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val restartReceiver: RestartReceiverUseCase,
    private val observeOverlay: ObserveOverlayPermissionUseCase,
    private val requestOverlay: RequestOverlayPermissionUseCase,
) : ViewModel() {

    val overlayGranted: StateFlow<Boolean> = observeOverlay()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun refreshOverlay() = observeOverlay.refresh()
    fun grantOverlay() = requestOverlay()

    val settings: StateFlow<ReceiverSettings> = observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceiverSettings())

    private var restartJob: Job? = null

    /** Settings advertised over mDNS only take effect after the receiver restarts. */
    fun update(requiresRestart: Boolean = false, transform: (ReceiverSettings) -> ReceiverSettings) {
        viewModelScope.launch {
            updateSettings(transform)
            if (requiresRestart) scheduleRestart()
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
