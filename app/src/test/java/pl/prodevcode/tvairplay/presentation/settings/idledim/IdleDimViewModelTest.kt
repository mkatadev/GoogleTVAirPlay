package pl.prodevcode.tvairplay.presentation.settings.idledim

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import pl.prodevcode.tvairplay.domain.model.IDLE_DIM_OPTIONS
import pl.prodevcode.tvairplay.domain.model.ReceiverSettings
import pl.prodevcode.tvairplay.domain.usecase.ObserveSettingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.UpdateSettingsUseCase
import pl.prodevcode.tvairplay.presentation.MainDispatcherRule
import pl.prodevcode.tvairplay.presentation.settings.settingsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class IdleDimViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val settings = MutableStateFlow(ReceiverSettings(idleDimMinutes = 5))
    private val repo = settingsRepository(settings)
    private fun viewModel() = IdleDimViewModel(ObserveSettingsUseCase(repo), UpdateSettingsUseCase(repo))

    @Test fun `state exposes options and the stored value`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(IDLE_DIM_OPTIONS, vm.state.value.options)
        assertEquals(5, vm.state.value.currentMinutes)
    }

    @Test fun `pick saves and closes`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(IdleDimIntent.Pick(0))
            assertEquals(IdleDimEffect.Close, awaitItem())
        }
        assertEquals(0, settings.value.idleDimMinutes)
    }
}
