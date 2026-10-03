package pl.prodevcode.tvairplay.presentation.settings.latency

import pl.prodevcode.tvairplay.domain.model.LatencyMode
import pl.prodevcode.tvairplay.presentation.mvi.UiEffect
import pl.prodevcode.tvairplay.presentation.mvi.UiIntent
import pl.prodevcode.tvairplay.presentation.mvi.UiState

data class LatencyUiState(val current: LatencyMode = LatencyMode.BALANCED) : UiState

sealed interface LatencyIntent : UiIntent {
    data class Pick(val mode: LatencyMode) : LatencyIntent
    data object Back : LatencyIntent
}

sealed interface LatencyEffect : UiEffect {
    data object Close : LatencyEffect
}
