package pl.prodevcode.tvairplay.presentation.diagnostics

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import pl.prodevcode.tvairplay.domain.model.DiagnosticsEvaluator
import pl.prodevcode.tvairplay.domain.usecase.ObserveDiagnosticsUseCase
import pl.prodevcode.tvairplay.domain.usecase.RestartReceiverUseCase
import pl.prodevcode.tvairplay.presentation.mvi.MviViewModel
import pl.prodevcode.tvairplay.presentation.diagnostics.DiagnosticsIntent as Intent

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    observeDiagnostics: ObserveDiagnosticsUseCase,
    private val restartReceiver: RestartReceiverUseCase,
) : MviViewModel<DiagnosticsUiState, Intent, DiagnosticsEffect>(DiagnosticsUiState()) {

    init {
        observeDiagnostics().reduceInto { copy(diagnostics = it, checks = DiagnosticsEvaluator.evaluate(it)) }
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            Intent.RestartReceiver -> restartReceiver()
        }
    }
}
