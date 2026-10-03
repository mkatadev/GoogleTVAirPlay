package pl.prodevcode.tvairplay.presentation.settings.latency

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import pl.prodevcode.tvairplay.domain.model.LatencyMode
import pl.prodevcode.tvairplay.domain.model.ReceiverSettings
import pl.prodevcode.tvairplay.domain.usecase.ObserveSettingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.UpdateSettingsUseCase
import pl.prodevcode.tvairplay.presentation.MainDispatcherRule
import pl.prodevcode.tvairplay.presentation.settings.settingsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class LatencyViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val settings = MutableStateFlow(ReceiverSettings())
    private val repo = settingsRepository(settings)
    private fun viewModel() = LatencyViewModel(ObserveSettingsUseCase(repo), UpdateSettingsUseCase(repo))

    @Test fun `state mirrors the stored mode`() = runTest {
        settings.value = ReceiverSettings(latencyMode = LatencyMode.SMOOTH)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(LatencyMode.SMOOTH, vm.state.value.current)
    }

    @Test fun `pick applies live and closes`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(LatencyIntent.Pick(LatencyMode.LOW))
            assertEquals(LatencyEffect.Close, awaitItem())
        }
        assertEquals(LatencyMode.LOW, settings.value.latencyMode)
    }
}
