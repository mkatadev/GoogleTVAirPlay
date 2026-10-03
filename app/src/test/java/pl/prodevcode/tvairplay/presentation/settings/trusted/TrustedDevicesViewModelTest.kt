package pl.prodevcode.tvairplay.presentation.settings.trusted

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import pl.prodevcode.tvairplay.domain.model.TrustedDevice
import pl.prodevcode.tvairplay.domain.repository.TrustedDevicesRepository
import pl.prodevcode.tvairplay.domain.usecase.ForgetTrustedDeviceUseCase
import pl.prodevcode.tvairplay.domain.usecase.ObserveTrustedDevicesUseCase
import pl.prodevcode.tvairplay.presentation.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class TrustedDevicesViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val stored = MutableStateFlow(listOf(TrustedDevice("pk1", "Ania's iPhone", 1, 2)))
    private val repo = mockk<TrustedDevicesRepository>(relaxed = true) { every { devices } returns stored }
    private fun viewModel() = TrustedDevicesViewModel(ObserveTrustedDevicesUseCase(repo), ForgetTrustedDeviceUseCase(repo))

    @Test fun `lists devices and forgets one or all`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals("Ania's iPhone", vm.state.value.devices.single().name)

        vm.onIntent(TrustedDevicesIntent.Forget("pk1"))
        verify { repo.forget("pk1") }
        vm.onIntent(TrustedDevicesIntent.Forget(null))
        verify { repo.forgetAll() }
    }

    @Test fun `back closes`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(TrustedDevicesIntent.Back)
            assertEquals(TrustedDevicesEffect.Close, awaitItem())
        }
    }
}
