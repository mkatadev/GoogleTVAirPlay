package pl.prodevcode.tvairplay.presentation.settings.devicename

import pl.prodevcode.tvairplay.presentation.mvi.UiEffect
import pl.prodevcode.tvairplay.presentation.mvi.UiIntent
import pl.prodevcode.tvairplay.presentation.mvi.UiState

data class DeviceNameUiState(
    val current: String = "",
    /** Text in the field; `null` until the user types, then it no longer follows [current]. */
    val draft: String? = null,
) : UiState {
    val text: String get() = draft ?: current
    val canSave: Boolean get() = text.trim().let { it.isNotEmpty() && it != current }
}

sealed interface DeviceNameIntent : UiIntent {
    data class DraftChanged(val text: String) : DeviceNameIntent
    /** Saves the trimmed draft and closes. */
    data object Save : DeviceNameIntent
    /** Picks a suggestion and closes. */
    data class Pick(val name: String) : DeviceNameIntent
    data object Back : DeviceNameIntent
}

sealed interface DeviceNameEffect : UiEffect {
    data object Close : DeviceNameEffect
}

const val DEVICE_NAME_MAX = 32
