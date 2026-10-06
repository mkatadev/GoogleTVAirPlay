package pl.prodevcode.tvairplay.presentation.settings

import pl.prodevcode.tvairplay.domain.model.HomeKitStatus
import pl.prodevcode.tvairplay.domain.model.ReceiverSettings
import pl.prodevcode.tvairplay.domain.model.InstallProgress
import pl.prodevcode.tvairplay.domain.model.UpdateCheck
import pl.prodevcode.tvairplay.presentation.mvi.UiEffect
import pl.prodevcode.tvairplay.presentation.mvi.UiIntent
import pl.prodevcode.tvairplay.presentation.mvi.UiState

/** Screens reachable from the settings list; each has its own ViewModel. */
enum class SettingsSub { DEVICE_NAME, LANGUAGE, IDLE_DIM, LATENCY, TRUSTED_DEVICES, HOMEKIT, DIAGNOSTICS, LICENSES }

data class SettingsUiState(
    val settings: ReceiverSettings = ReceiverSettings(),
    val overlayGranted: Boolean = true,
    val trustedDeviceCount: Int = 0,
    val homeKit: HomeKitStatus = HomeKitStatus(),
    val update: UpdateCheck = UpdateCheck.Idle,
    val install: InstallProgress = InstallProgress.Idle,
    /** Row to focus when the list comes back from a sub-screen. */
    val lastOpened: SettingsSub? = null,
) : UiState

sealed interface SettingsIntent : UiIntent {
    data object ScreenResumed : SettingsIntent
    data object CheckForUpdate : SettingsIntent
    data object InstallUpdate : SettingsIntent
    data object GrantOverlay : SettingsIntent
    data class Open(val sub: SettingsSub) : SettingsIntent

    data class SetStartOnBoot(val enabled: Boolean) : SettingsIntent
    data class SetRunInBackground(val enabled: Boolean) : SettingsIntent
    data class SetOpenAppOnConnect(val enabled: Boolean) : SettingsIntent
    data class SetRequirePin(val enabled: Boolean) : SettingsIntent
    data class SetRememberDevices(val enabled: Boolean) : SettingsIntent
    data class SetAdvertiseVideo(val enabled: Boolean) : SettingsIntent
    data class SetAdvertiseAudio(val enabled: Boolean) : SettingsIntent
    data class SetHevcEnabled(val enabled: Boolean) : SettingsIntent
    data class SetSubtitlesByDefault(val enabled: Boolean) : SettingsIntent
}

sealed interface SettingsEffect : UiEffect {
    data class Navigate(val sub: SettingsSub) : SettingsEffect
}
