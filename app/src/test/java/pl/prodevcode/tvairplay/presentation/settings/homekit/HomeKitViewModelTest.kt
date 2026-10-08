package pl.prodevcode.tvairplay.presentation.settings.homekit

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pl.prodevcode.tvairplay.domain.model.HomeKitStatus
import pl.prodevcode.tvairplay.domain.model.ReceiverSettings
import pl.prodevcode.tvairplay.domain.repository.HomeKitRepository
import pl.prodevcode.tvairplay.domain.usecase.ObserveHomeKitStatusUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveSettingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.ResetHomeKitPairingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.UpdateSettingsUseCase
import pl.prodevcode.tvairplay.presentation.MainDispatcherRule
import pl.prodevcode.tvairplay.presentation.settings.settingsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class HomeKitViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val settings = MutableStateFlow(ReceiverSettings(deviceName = "Salon", homeKitEnabled = true))
    private val settingsRepo = settingsRepository(settings)
    private val accessoryStatus = MutableStateFlow(HomeKitStatus(running = true, setupCode = "031-45-154", accessoryId = "AA:BB"))
    private val homeKitRepo = mockk<HomeKitRepository>(relaxed = true) { every { status } returns accessoryStatus }

    private fun viewModel() = HomeKitViewModel(
        ObserveSettingsUseCase(settingsRepo), UpdateSettingsUseCase(settingsRepo),
        ObserveHomeKitStatusUseCase(homeKitRepo), ResetHomeKitPairingsUseCase(homeKitRepo),
    )

    @Test fun `state mirrors settings and accessory status`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.state.value.enabled)
        assertEquals("Salon", vm.state.value.deviceName)
        assertEquals("031-45-154", vm.state.value.status.setupCode)

        accessoryStatus.value = HomeKitStatus(running = true, paired = true, controllers = 1, accessoryId = "AA:BB")
        advanceUntilIdle()
        assertTrue(vm.state.value.status.paired)
    }

    @Test fun `successful pairing closes the setup screen`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            advanceUntilIdle()
            accessoryStatus.value = HomeKitStatus(running = true, paired = true, controllers = 1, accessoryId = "AA:BB")
            advanceUntilIdle()
            assertEquals(HomeKitEffect.Close, awaitItem())
        }
    }

    @Test fun `opening an already paired accessory does not close the screen`() = runTest {
        accessoryStatus.value = HomeKitStatus(running = true, paired = true, controllers = 1, accessoryId = "AA:BB")
        val vm = viewModel()
        vm.effects.test {
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test fun `toggle writes the setting`() = runTest {
        val vm = viewModel()
        vm.onIntent(HomeKitIntent.SetEnabled(false))
        advanceUntilIdle()
        assertFalse(settings.value.homeKitEnabled)
    }

    @Test fun `reset asks for confirmation and only then forgets pairings`() = runTest {
        val vm = viewModel()
        vm.onIntent(HomeKitIntent.ResetPairings)
        assertTrue(vm.state.value.confirmReset)
        vm.onIntent(HomeKitIntent.CancelReset)
        assertFalse(vm.state.value.confirmReset)
        verify(exactly = 0) { homeKitRepo.resetPairings() }

        vm.onIntent(HomeKitIntent.ResetPairings)
        vm.onIntent(HomeKitIntent.ConfirmReset)
        assertFalse(vm.state.value.confirmReset)
        verify(exactly = 1) { homeKitRepo.resetPairings() }
    }

    @Test fun `tv control row opens accessibility settings`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(HomeKitIntent.OpenTvControl)
            assertEquals(HomeKitEffect.OpenAccessibilitySettings, awaitItem())
        }
    }

    @Test fun `back closes`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(HomeKitIntent.Back)
            assertEquals(HomeKitEffect.Close, awaitItem())
        }
    }
}

class HomeKitCodeLabelTest {
    @Test fun `formats the HAP code the way the Home app shows it`() {
        assertEquals("9359-6759", homeKitCodeLabel("935-96-759"))
        assertEquals("abc", homeKitCodeLabel("abc"))
    }
}
