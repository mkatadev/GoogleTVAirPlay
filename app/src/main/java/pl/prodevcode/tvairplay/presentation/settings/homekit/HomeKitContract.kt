package pl.prodevcode.tvairplay.presentation.settings.homekit

import pl.prodevcode.tvairplay.domain.model.HomeKitStatus
import pl.prodevcode.tvairplay.presentation.mvi.UiEffect
import pl.prodevcode.tvairplay.presentation.mvi.UiIntent
import pl.prodevcode.tvairplay.presentation.mvi.UiState

data class HomeKitUiState(
    val enabled: Boolean = false,
    /** Receiver name, i.e. how the TV is listed in Home while pairing. */
    val deviceName: String = "",
    val status: HomeKitStatus = HomeKitStatus(),
    val confirmReset: Boolean = false,
) : UiState

sealed interface HomeKitIntent : UiIntent {
    data class SetEnabled(val enabled: Boolean) : HomeKitIntent
    data object ResetPairings : HomeKitIntent
    data object ConfirmReset : HomeKitIntent
    data object CancelReset : HomeKitIntent
    /** Row for the TV-remote accessibility service; it can only be switched in system settings. */
    data object OpenTvControl : HomeKitIntent
    data object Back : HomeKitIntent
}

sealed interface HomeKitEffect : UiEffect {
    data object Close : HomeKitEffect
    data object OpenAccessibilitySettings : HomeKitEffect
}
