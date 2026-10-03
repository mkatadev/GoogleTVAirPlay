package pl.prodevcode.tvairplay.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.prodevcode.tvairplay.domain.model.Diagnostics

interface DiagnosticsRepository {
    val diagnostics: Flow<Diagnostics>
}
