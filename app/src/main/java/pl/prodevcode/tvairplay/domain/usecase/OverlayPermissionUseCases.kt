package pl.prodevcode.tvairplay.domain.usecase

import javax.inject.Inject
import pl.prodevcode.tvairplay.domain.repository.OverlayPermissionRepository

class ObserveOverlayPermissionUseCase @Inject constructor(private val repo: OverlayPermissionRepository) {
    operator fun invoke() = repo.granted
    fun refresh() = repo.refresh()
}

class RequestOverlayPermissionUseCase @Inject constructor(private val repo: OverlayPermissionRepository) {
    operator fun invoke() = repo.openSystemSettings()
}
