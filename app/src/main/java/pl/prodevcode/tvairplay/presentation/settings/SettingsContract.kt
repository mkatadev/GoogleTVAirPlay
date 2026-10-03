package pl.prodevcode.tvairplay.presentation.settings

import pl.prodevcode.tvairplay.domain.model.ReceiverSettings
import pl.prodevcode.tvairplay.domain.model.TrustedDevice
import pl.prodevcode.tvairplay.presentation.mvi.NoEffect
import pl.prodevcode.tvairplay.presentation.mvi.UiIntent
import pl.prodevcode.tvairplay.presentation.mvi.UiState

data class SettingsUiState(
    val settings: ReceiverSettings = ReceiverSettings(),
    val overlayGranted: Boolean = true,
    val pickingDeviceName: Boolean = false,
    val trustedDevices: List<TrustedDevice> = emptyList(),
    val managingTrustedDevices: Boolean = false,
) : UiState

sealed interface SettingsIntent : UiIntent {
    data object ScreenResumed : SettingsIntent
    data object GrantOverlay : SettingsIntent

    data object PickDeviceName : SettingsIntent
    /** `null` = picker dismissed without a choice. */
    data class DeviceNamePicked(val name: String?) : SettingsIntent

    data class SetStartOnBoot(val enabled: Boolean) : SettingsIntent
    data class SetRunInBackground(val enabled: Boolean) : SettingsIntent
    data class SetOpenAppOnConnect(val enabled: Boolean) : SettingsIntent
    data class SetRequirePin(val enabled: Boolean) : SettingsIntent
    data class SetRememberDevices(val enabled: Boolean) : SettingsIntent
    data object ManageTrustedDevices : SettingsIntent
    data object CloseTrustedDevices : SettingsIntent
    /** `null` forgets all. */
    data class ForgetTrustedDevice(val id: String?) : SettingsIntent
    data class SetAdvertiseVideo(val enabled: Boolean) : SettingsIntent
    data class SetAdvertiseAudio(val enabled: Boolean) : SettingsIntent
    data class SetHevcEnabled(val enabled: Boolean) : SettingsIntent
}

typealias SettingsEffect = NoEffect
