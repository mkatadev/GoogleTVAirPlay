package pl.prodevcode.tvairplay.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import pl.prodevcode.tvairplay.domain.model.Diagnostics
import pl.prodevcode.tvairplay.domain.repository.DiagnosticsRepository

class ObserveDiagnosticsUseCase @Inject constructor(private val repo: DiagnosticsRepository) {
    operator fun invoke(): Flow<Diagnostics> = repo.diagnostics
}
