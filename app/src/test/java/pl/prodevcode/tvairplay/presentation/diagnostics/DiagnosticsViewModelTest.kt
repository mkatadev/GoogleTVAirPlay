package pl.prodevcode.tvairplay.presentation.diagnostics

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
import pl.prodevcode.tvairplay.domain.model.CheckResult
import pl.prodevcode.tvairplay.domain.model.DiagnosticCheck
import pl.prodevcode.tvairplay.domain.model.Diagnostics
import pl.prodevcode.tvairplay.domain.model.NetworkDiagnostics
import pl.prodevcode.tvairplay.domain.model.ReceiverStatus
import pl.prodevcode.tvairplay.domain.repository.DiagnosticsRepository
import pl.prodevcode.tvairplay.domain.repository.ReceiverRepository
import pl.prodevcode.tvairplay.domain.usecase.ObserveDiagnosticsUseCase
import pl.prodevcode.tvairplay.domain.usecase.RestartReceiverUseCase
import pl.prodevcode.tvairplay.presentation.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val diagnostics = MutableStateFlow(Diagnostics())
    private val repo = mockk<DiagnosticsRepository> { every { this@mockk.diagnostics } returns this@DiagnosticsViewModelTest.diagnostics }
    private val receiver = mockk<ReceiverRepository>(relaxed = true)

    private fun viewModel() = DiagnosticsViewModel(ObserveDiagnosticsUseCase(repo), RestartReceiverUseCase(receiver))

    @Test fun `checks follow the diagnostics stream`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(CheckResult.ERROR, vm.state.value.checks[DiagnosticCheck.NETWORK])

        diagnostics.value = Diagnostics(
            receiverStatus = ReceiverStatus.RUNNING, port = 7000,
            network = NetworkDiagnostics(addresses = listOf("10.0.0.2")),
        )
        advanceUntilIdle()
        assertEquals(CheckResult.OK, vm.state.value.checks[DiagnosticCheck.NETWORK])
        assertEquals(7000, vm.state.value.diagnostics.port)
    }

    @Test fun `restart intent restarts the receiver`() = runTest {
        viewModel().onIntent(DiagnosticsIntent.RestartReceiver)
        verify { receiver.restart() }
    }
}
