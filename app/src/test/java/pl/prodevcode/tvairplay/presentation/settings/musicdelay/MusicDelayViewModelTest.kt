package pl.prodevcode.tvairplay.presentation.settings.musicdelay

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import pl.prodevcode.tvairplay.domain.model.MusicDelay
import pl.prodevcode.tvairplay.domain.model.ReceiverSettings
import pl.prodevcode.tvairplay.domain.usecase.ObserveSettingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.UpdateSettingsUseCase
import pl.prodevcode.tvairplay.presentation.MainDispatcherRule
import pl.prodevcode.tvairplay.presentation.settings.settingsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class MusicDelayViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val settings = MutableStateFlow(ReceiverSettings())
    private val repo = settingsRepository(settings)
    private fun viewModel() = MusicDelayViewModel(ObserveSettingsUseCase(repo), UpdateSettingsUseCase(repo))

    @Test fun `defaults to the AirPlay schedule and mirrors the stored choice`() = runTest {
        assertEquals(MusicDelay.AIRPLAY, viewModel().state.value.current)
        settings.value = ReceiverSettings(musicDelay = MusicDelay.SHORT)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(MusicDelay.SHORT, vm.state.value.current)
    }

    @Test fun `pick applies live and closes`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(MusicDelayIntent.Pick(MusicDelay.MEDIUM))
            assertEquals(MusicDelayEffect.Close, awaitItem())
        }
        assertEquals(MusicDelay.MEDIUM, settings.value.musicDelay)
    }

    @Test fun `back closes without touching the setting`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(MusicDelayIntent.Back)
            assertEquals(MusicDelayEffect.Close, awaitItem())
        }
        assertEquals(MusicDelay.AIRPLAY, settings.value.musicDelay)
    }
}
