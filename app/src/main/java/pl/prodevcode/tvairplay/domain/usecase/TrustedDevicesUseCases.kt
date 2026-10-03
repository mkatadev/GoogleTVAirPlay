package pl.prodevcode.tvairplay.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import pl.prodevcode.tvairplay.domain.model.TrustedDevice
import pl.prodevcode.tvairplay.domain.repository.TrustedDevicesRepository

class ObserveTrustedDevicesUseCase @Inject constructor(private val repo: TrustedDevicesRepository) {
    operator fun invoke(): Flow<List<TrustedDevice>> = repo.devices
}

class ForgetTrustedDeviceUseCase @Inject constructor(private val repo: TrustedDevicesRepository) {
    /** `null` forgets every device. */
    operator fun invoke(id: String?) = if (id == null) repo.forgetAll() else repo.forget(id)
}
