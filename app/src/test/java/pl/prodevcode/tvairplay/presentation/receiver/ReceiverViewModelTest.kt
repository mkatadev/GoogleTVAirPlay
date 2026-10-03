package pl.prodevcode.tvairplay.presentation.receiver

import app.cash.turbine.test
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pl.prodevcode.tvairplay.domain.model.AppUpdate
import pl.prodevcode.tvairplay.domain.model.DeviceInfo
import pl.prodevcode.tvairplay.domain.model.InstallProgress
import pl.prodevcode.tvairplay.domain.model.UpdateCheck
import pl.prodevcode.tvairplay.domain.model.NowPlaying
import pl.prodevcode.tvairplay.domain.model.SessionMode
import pl.prodevcode.tvairplay.domain.model.ReceiverSettings
import pl.prodevcode.tvairplay.domain.model.ReceiverState
import pl.prodevcode.tvairplay.domain.model.ReceiverStatus
import pl.prodevcode.tvairplay.domain.repository.DeviceInfoRepository
import pl.prodevcode.tvairplay.domain.repository.OverlayPermissionRepository
import pl.prodevcode.tvairplay.domain.repository.ReceiverRepository
import pl.prodevcode.tvairplay.domain.repository.SettingsRepository
import pl.prodevcode.tvairplay.domain.repository.UpdateRepository
import pl.prodevcode.tvairplay.domain.usecase.ObserveDeviceInfoUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveOverlayPermissionUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveReceiverStateUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveSettingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.PlaybackControlUseCase
import pl.prodevcode.tvairplay.domain.usecase.RequestOverlayPermissionUseCase
import pl.prodevcode.tvairplay.domain.usecase.StartReceiverUseCase
import pl.prodevcode.tvairplay.domain.usecase.StopReceiverUseCase
import pl.prodevcode.tvairplay.domain.usecase.ToggleReceiverUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveUpdateUseCase
import pl.prodevcode.tvairplay.domain.usecase.CheckForUpdateUseCase
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
        observeUpdate = ObserveUpdateUseCase(updateRepo),
        checkForUpdate = CheckForUpdateUseCase(updateRepo),
    )
    private val updateState = MutableStateFlow<UpdateCheck>(UpdateCheck.Idle)
    private val updateRepo = mockk<UpdateRepository>(relaxed = true) {
        every { state } returns updateState
        every { install } returns MutableStateFlow(InstallProgress.Idle)
    }

    @Test fun `idle screen learns about a newer release`() = runTest {
        val vm = viewModel()
        vm.onIntent(ReceiverIntent.EnsureStarted)
        advanceUntilIdle()
        coVerify { updateRepo.check(false) }
        assertEquals(null, vm.state.value.updateAvailable)
        updateState.value = UpdateCheck.Available(AppUpdate("1.0.0", "1.1.0", "https://x"))
        advanceUntilIdle()
        assertEquals("1.1.0", vm.state.value.updateAvailable)
    }

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

    @Test fun `audio session dims after the idle timeout and any key wakes it`() = runTest {
        settingsFlow.value = ReceiverSettings(idleDimMinutes = 1)
        receiverState.value = ReceiverState(
            status = ReceiverStatus.RUNNING, mode = SessionMode.AUDIO, connectedClients = 1,
            nowPlaying = NowPlaying(title = "Song", playing = true),
        )
        val vm = viewModel()
        runCurrent()
        assertFalse(vm.state.value.dimmed)

        advanceTimeBy(61_000)
        assertTrue(vm.state.value.dimmed)
        assertTrue("playing music keeps the panel on", vm.state.value.keepScreenOn)

        receiverState.value = receiverState.value.copy(nowPlaying = NowPlaying(title = "Song", playing = false))
        runCurrent()
        // pause is activity: timer restarts, then paused + dimmed lets the TV sleep
        assertFalse(vm.state.value.dimmed)
        advanceTimeBy(61_000)
        assertTrue(vm.state.value.dimmed)
        assertFalse(vm.state.value.keepScreenOn)

        vm.onIntent(ReceiverIntent.UserInteraction)
        runCurrent()
        assertFalse(vm.state.value.dimmed)
        assertTrue(vm.state.value.keepScreenOn)
    }

    @Test fun `mirroring never dims and dimming can be disabled`() = runTest {
        settingsFlow.value = ReceiverSettings(idleDimMinutes = 1)
        receiverState.value = ReceiverState(status = ReceiverStatus.RUNNING, mode = SessionMode.MIRRORING, connectedClients = 1)
        val vm = viewModel()
        advanceTimeBy(120_000)
        assertFalse(vm.state.value.dimmed)

        settingsFlow.value = ReceiverSettings(idleDimMinutes = 0)
        receiverState.value = receiverState.value.copy(mode = SessionMode.AUDIO, nowPlaying = NowPlaying(title = "x", playing = true))
        advanceTimeBy(600_000)
        assertFalse(vm.state.value.dimmed)
    }

    @Test fun `transport intents are forwarded`() = runTest {
        val vm = viewModel()
        vm.onIntent(ReceiverIntent.PlayPause)
        vm.onIntent(ReceiverIntent.SeekTo(5_000))
        vm.onIntent(ReceiverIntent.Next)
        verify { receiverRepo.togglePlayPause(); receiverRepo.seekTo(5_000); receiverRepo.skipNext() }
    }
}
