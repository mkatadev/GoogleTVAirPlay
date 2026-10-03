package pl.prodevcode.tvairplay.presentation.settings.idledim

import pl.prodevcode.tvairplay.domain.model.IDLE_DIM_OPTIONS
import pl.prodevcode.tvairplay.presentation.mvi.UiEffect
import pl.prodevcode.tvairplay.presentation.mvi.UiIntent
import pl.prodevcode.tvairplay.presentation.mvi.UiState

data class IdleDimUiState(
    val currentMinutes: Int = 0,
    val options: List<Int> = IDLE_DIM_OPTIONS,
) : UiState

sealed interface IdleDimIntent : UiIntent {
    data class Pick(val minutes: Int) : IdleDimIntent
    data object Back : IdleDimIntent
}

sealed interface IdleDimEffect : UiEffect {
    data object Close : IdleDimEffect
}
