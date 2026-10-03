package pl.prodevcode.tvairplay.presentation.licenses

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import pl.prodevcode.tvairplay.domain.model.OpenSourceComponent
import pl.prodevcode.tvairplay.domain.repository.LicensesRepository
import pl.prodevcode.tvairplay.domain.usecase.GetLicenseTextUseCase
import pl.prodevcode.tvairplay.domain.usecase.GetOpenSourceComponentsUseCase
import pl.prodevcode.tvairplay.presentation.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class LicensesViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val uxplay = OpenSourceComponent("UxPlay", "GPL-3.0", "https://github.com/FDH2/UxPlay", "uxplay.txt")
    private val repo = mockk<LicensesRepository> {
        every { components() } returns listOf(uxplay)
        coEvery { licenseText("uxplay.txt") } returns "GNU GPL v3"
    }

    private fun viewModel() = LicensesViewModel(GetOpenSourceComponentsUseCase(repo), GetLicenseTextUseCase(repo))

    @Test fun `selecting a component loads its license text`() = runTest {
        val vm = viewModel()
        vm.onIntent(LicensesIntent.Select(uxplay))
        advanceUntilIdle()
        assertEquals(uxplay, vm.state.value.selected)
        assertEquals("GNU GPL v3", vm.state.value.licenseText)
    }

    @Test fun `back closes the detail first, then leaves the screen`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(LicensesIntent.Select(uxplay))
            vm.onIntent(LicensesIntent.Back)
            assertNull(vm.state.value.selected)
            expectNoEvents()

            vm.onIntent(LicensesIntent.Back)
            assertEquals(LicensesEffect.NavigateBack, awaitItem())
        }
    }
}
