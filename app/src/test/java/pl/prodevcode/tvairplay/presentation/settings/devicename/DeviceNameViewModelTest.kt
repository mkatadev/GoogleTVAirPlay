package pl.prodevcode.tvairplay.presentation.settings.devicename

import app.cash.turbine.test
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
import pl.prodevcode.tvairplay.domain.model.ReceiverSettings
import pl.prodevcode.tvairplay.domain.repository.ReceiverRepository
import pl.prodevcode.tvairplay.domain.usecase.ObserveSettingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.RestartReceiverUseCase
import pl.prodevcode.tvairplay.domain.usecase.UpdateSettingsUseCase
import pl.prodevcode.tvairplay.presentation.MainDispatcherRule
import pl.prodevcode.tvairplay.presentation.settings.settingsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceNameViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val settings = MutableStateFlow(ReceiverSettings(deviceName = "Google TV"))
    private val repo = settingsRepository(settings)
    private val receiverRepo = mockk<ReceiverRepository>(relaxed = true)
    private fun viewModel() = DeviceNameViewModel(
        ObserveSettingsUseCase(repo), UpdateSettingsUseCase(repo), RestartReceiverUseCase(receiverRepo),
    )

    @Test fun `draft follows the stored name until the user types`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals("Google TV", vm.state.value.text)
        assertFalse(vm.state.value.canSave)

        vm.onIntent(DeviceNameIntent.DraftChanged("  Living Room TV "))
        assertEquals("  Living Room TV ", vm.state.value.text)
        assertTrue(vm.state.value.canSave)

        vm.onIntent(DeviceNameIntent.DraftChanged("   "))
        assertFalse(vm.state.value.canSave)
    }

    @Test fun `draft is capped at the maximum length`() = runTest {
        val vm = viewModel()
        vm.onIntent(DeviceNameIntent.DraftChanged("x".repeat(100)))
        assertEquals(DEVICE_NAME_MAX, vm.state.value.text.length)
    }

    @Test fun `save trims, persists, restarts the receiver and closes`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(DeviceNameIntent.DraftChanged(" Kitchen TV "))
        vm.effects.test {
            vm.onIntent(DeviceNameIntent.Save)
            assertEquals(DeviceNameEffect.Close, awaitItem())
        }
        assertEquals("Kitchen TV", settings.value.deviceName)
        verify(exactly = 1) { receiverRepo.restart() }
    }

    @Test fun `picking the current name just closes`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.effects.test {
            vm.onIntent(DeviceNameIntent.Pick("Google TV"))
            assertEquals(DeviceNameEffect.Close, awaitItem())
        }
        verify(exactly = 0) { receiverRepo.restart() }
    }

    @Test fun `save with nothing to save is ignored`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.effects.test {
            vm.onIntent(DeviceNameIntent.Save)
            expectNoEvents()
        }
        verify(exactly = 0) { receiverRepo.restart() }
    }
}
