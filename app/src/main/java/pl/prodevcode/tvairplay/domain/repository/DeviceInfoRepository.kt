package pl.prodevcode.tvairplay.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.prodevcode.tvairplay.domain.model.DeviceInfo

interface DeviceInfoRepository {
    val deviceInfo: Flow<DeviceInfo>
}
