package pl.prodevcode.tvairplay.presentation.receiver

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
import pl.prodevcode.tvairplay.domain.model.DeviceInfo
import pl.prodevcode.tvairplay.domain.model.ReceiverSettings
import pl.prodevcode.tvairplay.domain.model.ReceiverState
import pl.prodevcode.tvairplay.domain.model.ReceiverStatus
import pl.prodevcode.tvairplay.domain.repository.DeviceInfoRepository
import pl.prodevcode.tvairplay.domain.repository.OverlayPermissionRepository
import pl.prodevcode.tvairplay.domain.repository.ReceiverRepository
import pl.prodevcode.tvairplay.domain.repository.SettingsRepository
import pl.prodevcode.tvairplay.domain.usecase.ObserveDeviceInfoUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveOverlayPermissionUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveReceiverStateUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveSettingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.PlaybackControlUseCase
import pl.prodevcode.tvairplay.domain.usecase.RequestOverlayPermissionUseCase
import pl.prodevcode.tvairplay.domain.usecase.StartReceiverUseCase
import pl.prodevcode.tvairplay.domain.usecase.StopReceiverUseCase
import pl.prodevcode.tvairplay.domain.usecase.ToggleReceiverUseCase
import pl.prodevcode.tvairplay.platform.VideoSurfaceHost
import pl.prodevcode.tvairplay.presentation.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiverViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val receiverState = MutableStateFlow(ReceiverState())
    private val settingsFlow = MutableStateFlow(ReceiverSettings())
    private val overlay = MutableStateFlow(true)

    private val receiverRepo = mockk<ReceiverRepository>(relaxed = true) { every { state } returns receiverState }
    private val settingsRepo = mockk<SettingsRepository> { every { settings } returns settingsFlow }
    private val deviceRepo = mockk<DeviceInfoRepository> {
        every { deviceInfo } returns MutableStateFlow(DeviceInfo("Salon TV", "192.168.1.5"))
    }
    private val overlayRepo = mockk<OverlayPermissionRepository>(relaxed = true) { every { granted } returns overlay }

    private fun viewModel() = ReceiverViewModel(
        observeState = ObserveReceiverStateUseCase(receiverRepo),
        observeDevice = ObserveDeviceInfoUseCase(deviceRepo),
        observeSettings = ObserveSettingsUseCase(settingsRepo),
        observeOverlay = ObserveOverlayPermissionUseCase(overlayRepo),
        requestOverlay = RequestOverlayPermissionUseCase(overlayRepo),
        startReceiver = StartReceiverUseCase(receiverRepo),
        toggleReceiver = ToggleReceiverUseCase(receiverRepo),
        stopReceiver = StopReceiverUseCase(receiverRepo),
        playback = PlaybackControlUseCase(receiverRepo),
        surfaces = mockk<VideoSurfaceHost>(relaxed = true),
    )

    @Test fun `state combines receiver, device, settings and overlay`() = runTest {
        receiverState.value = ReceiverState(status = ReceiverStatus.RUNNING, connectedClients = 1)
        val vm = viewModel()
        vm.state.test {
            skipItems(1) // initial
            val ui = awaitItem()
            assertEquals("Salon TV", ui.device.name)
            assertEquals(ReceiverStatus.RUNNING, ui.receiver.status)
            assertTrue(ui.keepScreenOn)
            assertTrue(ui.overlayPermissionGranted)
        }
    }

    @Test fun `missing overlay permission only matters when opening on connect`() = runTest {
        overlay.value = false
        settingsFlow.value = ReceiverSettings(openAppOnConnect = false)
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.state.value.overlayPermissionGranted)

        settingsFlow.value = ReceiverSettings(openAppOnConnect = true)
        advanceUntilIdle()
        assertFalse(vm.state.value.overlayPermissionGranted)
    }

    @Test fun `backgrounding stops the receiver only when idle and not allowed in background`() = runTest {
        settingsFlow.value = ReceiverSettings(runInBackground = false)
        receiverState.value = ReceiverState(connectedClients = 1)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(ReceiverIntent.AppBackgrounded)
        verify(exactly = 0) { receiverRepo.stop() }

        receiverState.value = ReceiverState(connectedClients = 0)
        advanceUntilIdle()
        vm.onIntent(ReceiverIntent.AppBackgrounded)
        verify(exactly = 1) { receiverRepo.stop() }
    }

    @Test fun `toggle uses the current receiver status`() = runTest {
        receiverState.value = ReceiverState(status = ReceiverStatus.RUNNING)
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(ReceiverIntent.ToggleReceiver)
        verify { receiverRepo.stop() }

        receiverState.value = ReceiverState(status = ReceiverStatus.STOPPED)
        advanceUntilIdle()
        vm.onIntent(ReceiverIntent.ToggleReceiver)
        verify { receiverRepo.start() }
    }

    @Test fun `transport intents are forwarded`() = runTest {
        val vm = viewModel()
        vm.onIntent(ReceiverIntent.PlayPause)
        vm.onIntent(ReceiverIntent.SeekTo(5_000))
        vm.onIntent(ReceiverIntent.Next)
        verify { receiverRepo.togglePlayPause(); receiverRepo.seekTo(5_000); receiverRepo.skipNext() }
    }
}
