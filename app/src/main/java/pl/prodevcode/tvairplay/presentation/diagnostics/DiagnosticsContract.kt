package pl.prodevcode.tvairplay.presentation.diagnostics

import pl.prodevcode.tvairplay.domain.model.CheckResult
import pl.prodevcode.tvairplay.domain.model.DiagnosticCheck
import pl.prodevcode.tvairplay.domain.model.Diagnostics
import pl.prodevcode.tvairplay.presentation.mvi.NoEffect
import pl.prodevcode.tvairplay.presentation.mvi.UiIntent
import pl.prodevcode.tvairplay.presentation.mvi.UiState

data class DiagnosticsUiState(
    val diagnostics: Diagnostics = Diagnostics(),
    val checks: Map<DiagnosticCheck, CheckResult> = emptyMap(),
) : UiState

sealed interface DiagnosticsIntent : UiIntent {
    data object RestartReceiver : DiagnosticsIntent
}

typealias DiagnosticsEffect = NoEffect
