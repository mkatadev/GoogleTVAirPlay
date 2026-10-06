package pl.prodevcode.tvairplay.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import pl.prodevcode.tvairplay.domain.model.HomeKitStatus
import pl.prodevcode.tvairplay.domain.repository.HomeKitRepository

class ObserveHomeKitStatusUseCase @Inject constructor(private val repo: HomeKitRepository) {
    operator fun invoke(): Flow<HomeKitStatus> = repo.status
}

class ResetHomeKitPairingsUseCase @Inject constructor(private val repo: HomeKitRepository) {
    operator fun invoke() = repo.resetPairings()
}
