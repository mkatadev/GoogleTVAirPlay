package pl.prodevcode.tvairplay.presentation.settings

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pl.prodevcode.tvairplay.domain.model.ReceiverSettings
import pl.prodevcode.tvairplay.domain.repository.OverlayPermissionRepository
import pl.prodevcode.tvairplay.domain.repository.ReceiverRepository
import pl.prodevcode.tvairplay.domain.repository.SettingsRepository
import pl.prodevcode.tvairplay.domain.usecase.ObserveOverlayPermissionUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveSettingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.RequestOverlayPermissionUseCase
import pl.prodevcode.tvairplay.domain.usecase.RestartReceiverUseCase
import pl.prodevcode.tvairplay.domain.usecase.UpdateSettingsUseCase
import pl.prodevcode.tvairplay.presentation.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val settingsFlow = MutableStateFlow(ReceiverSettings())
    private val settingsRepo = mockk<SettingsRepository> {
        every { settings } returns settingsFlow
        coEvery { update(any()) } answers {
            settingsFlow.value = firstArg<(ReceiverSettings) -> ReceiverSettings>()(settingsFlow.value)
        }
    }
    private val receiverRepo = mockk<ReceiverRepository>(relaxed = true)
    private val overlayRepo = mockk<OverlayPermissionRepository>(relaxed = true) {
        every { granted } returns MutableStateFlow(false)
    }

    private fun viewModel() = SettingsViewModel(
        observeSettings = ObserveSettingsUseCase(settingsRepo),
        updateSettings = UpdateSettingsUseCase(settingsRepo),
        restartReceiver = RestartReceiverUseCase(receiverRepo),
        observeOverlay = ObserveOverlayPermissionUseCase(overlayRepo),
        requestOverlay = RequestOverlayPermissionUseCase(overlayRepo),
    )

    @Test fun `mDNS-relevant settings restart the receiver after a debounce`() = runTest {
        val vm = viewModel()
        vm.onIntent(SettingsIntent.SetRequirePin(true))
        advanceTimeBy(500)
        verify(exactly = 0) { receiverRepo.restart() }
        advanceTimeBy(1_000)
        verify(exactly = 1) { receiverRepo.restart() }
        assertTrue(vm.state.value.settings.requirePin)
    }

    @Test fun `local settings do not restart the receiver`() = runTest {
        val vm = viewModel()
        vm.onIntent(SettingsIntent.SetStartOnBoot(false))
        advanceUntilIdle()
        verify(exactly = 0) { receiverRepo.restart() }
        assertFalse(vm.state.value.settings.startOnBoot)
    }

    @Test fun `rapid changes collapse into one restart`() = runTest {
        val vm = viewModel()
        vm.onIntent(SettingsIntent.SetAdvertiseVideo(false))
        vm.onIntent(SettingsIntent.SetAdvertiseAudio(false))
        vm.onIntent(SettingsIntent.SetHevcEnabled(false))
        advanceUntilIdle()
        verify(exactly = 1) { receiverRepo.restart() }
    }

    @Test fun `device name picker flow`() = runTest {
        val vm = viewModel()
        vm.onIntent(SettingsIntent.PickDeviceName)
        assertTrue(vm.state.value.pickingDeviceName)

        vm.onIntent(SettingsIntent.DeviceNamePicked(null))
        advanceUntilIdle()
        assertFalse(vm.state.value.pickingDeviceName)
        coVerify(exactly = 0) { settingsRepo.update(any()) }

        vm.onIntent(SettingsIntent.PickDeviceName)
        vm.onIntent(SettingsIntent.DeviceNamePicked("Kuchnia TV"))
        advanceUntilIdle()
        assertEquals("Kuchnia TV", vm.state.value.settings.deviceName)
        verify(exactly = 1) { receiverRepo.restart() }
    }

    @Test fun `overlay state is observed and refreshable`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertFalse(vm.state.value.overlayGranted)
        vm.onIntent(SettingsIntent.ScreenResumed)
        verify { overlayRepo.refresh() }
        vm.onIntent(SettingsIntent.GrantOverlay)
        verify { overlayRepo.openSystemSettings() }
    }
}
