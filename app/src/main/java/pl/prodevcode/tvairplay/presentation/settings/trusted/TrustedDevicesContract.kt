package pl.prodevcode.tvairplay.presentation.settings.trusted

import pl.prodevcode.tvairplay.domain.model.TrustedDevice
import pl.prodevcode.tvairplay.presentation.mvi.UiEffect
import pl.prodevcode.tvairplay.presentation.mvi.UiIntent
import pl.prodevcode.tvairplay.presentation.mvi.UiState

data class TrustedDevicesUiState(val devices: List<TrustedDevice> = emptyList()) : UiState

sealed interface TrustedDevicesIntent : UiIntent {
    /** `null` forgets all. */
    data class Forget(val id: String?) : TrustedDevicesIntent
    data object Back : TrustedDevicesIntent
}

sealed interface TrustedDevicesEffect : UiEffect {
    data object Close : TrustedDevicesEffect
}
