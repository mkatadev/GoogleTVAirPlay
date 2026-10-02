package pl.prodevcode.tvairplay.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import pl.prodevcode.tvairplay.domain.model.DeviceInfo
import pl.prodevcode.tvairplay.domain.model.ReceiverSettings
import pl.prodevcode.tvairplay.domain.repository.DeviceInfoRepository
import pl.prodevcode.tvairplay.domain.repository.SettingsRepository

class ObserveSettingsUseCase @Inject constructor(private val repo: SettingsRepository) {
    operator fun invoke(): Flow<ReceiverSettings> = repo.settings
}

class UpdateSettingsUseCase @Inject constructor(private val repo: SettingsRepository) {
    suspend operator fun invoke(transform: (ReceiverSettings) -> ReceiverSettings) = repo.update(transform)
}

class ObserveDeviceInfoUseCase @Inject constructor(private val repo: DeviceInfoRepository) {
    operator fun invoke(): Flow<DeviceInfo> = repo.deviceInfo
}
