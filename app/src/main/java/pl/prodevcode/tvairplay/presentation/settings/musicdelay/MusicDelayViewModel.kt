package pl.prodevcode.tvairplay.presentation.settings.musicdelay

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import pl.prodevcode.tvairplay.domain.usecase.ObserveSettingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.UpdateSettingsUseCase
import pl.prodevcode.tvairplay.presentation.mvi.MviViewModel

@HiltViewModel
class MusicDelayViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
) : MviViewModel<MusicDelayUiState, MusicDelayIntent, MusicDelayEffect>(MusicDelayUiState()) {

    init {
        observeSettings().reduceInto { copy(current = it.musicDelay) }
    }

    override fun onIntent(intent: MusicDelayIntent) {
        when (intent) {
            is MusicDelayIntent.Pick -> viewModelScope.launch {
                updateSettings { it.copy(musicDelay = intent.delay) }
                sendEffect(MusicDelayEffect.Close)
            }
            MusicDelayIntent.Back -> sendEffect(MusicDelayEffect.Close)
        }
    }
}
