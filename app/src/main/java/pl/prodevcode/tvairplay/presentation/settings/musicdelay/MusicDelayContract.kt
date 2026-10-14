package pl.prodevcode.tvairplay.presentation.settings.musicdelay

import pl.prodevcode.tvairplay.domain.model.MusicDelay
import pl.prodevcode.tvairplay.presentation.mvi.UiEffect
import pl.prodevcode.tvairplay.presentation.mvi.UiIntent
import pl.prodevcode.tvairplay.presentation.mvi.UiState

data class MusicDelayUiState(val current: MusicDelay = MusicDelay.AIRPLAY) : UiState

sealed interface MusicDelayIntent : UiIntent {
    data class Pick(val delay: MusicDelay) : MusicDelayIntent
    data object Back : MusicDelayIntent
}

sealed interface MusicDelayEffect : UiEffect {
    data object Close : MusicDelayEffect
}
