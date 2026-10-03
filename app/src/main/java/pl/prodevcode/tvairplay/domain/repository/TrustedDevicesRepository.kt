package pl.prodevcode.tvairplay.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.prodevcode.tvairplay.domain.model.TrustedDevice

interface TrustedDevicesRepository {
    val devices: Flow<List<TrustedDevice>>
    fun forget(id: String)
    fun forgetAll()
}
