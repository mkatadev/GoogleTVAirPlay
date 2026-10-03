package pl.prodevcode.tvairplay.data.security

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import pl.prodevcode.tvairplay.data.receiver.AirPlayServiceConnector
import pl.prodevcode.tvairplay.domain.model.TrustedDevice
import pl.prodevcode.tvairplay.domain.repository.TrustedDevicesRepository

/** The store lives in the service so the native pair-verify path and the UI share one source of truth. */
@Singleton
class TrustedDevicesRepositoryImpl @Inject constructor(
    private val connector: AirPlayServiceConnector,
) : TrustedDevicesRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val devices: Flow<List<TrustedDevice>> = connector.service.flatMapLatest { svc ->
        svc?.trustedDevices?.devices?.map { list ->
            list.map { TrustedDevice(id = it.publicKey, name = it.name, addedAt = it.addedAt, lastSeenAt = it.lastSeenAt) }
                .sortedByDescending { it.lastSeenAt }
        } ?: flowOf(emptyList())
    }

    override fun forget(id: String) { connector.service.value?.trustedDevices?.forget(id) }
    override fun forgetAll() { connector.service.value?.trustedDevices?.forgetAll() }
}
