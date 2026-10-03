package pl.prodevcode.tvairplay.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import pl.prodevcode.tvairplay.domain.model.UpdateCheck
import pl.prodevcode.tvairplay.domain.repository.UpdateRepository

class ObserveUpdateUseCase @Inject constructor(private val repo: UpdateRepository) {
    operator fun invoke(): Flow<UpdateCheck> = repo.state
}

class CheckForUpdateUseCase @Inject constructor(private val repo: UpdateRepository) {
    suspend operator fun invoke(force: Boolean = false) = repo.check(force)
}
