package pl.prodevcode.tvairplay.data.homekit

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import pl.prodevcode.tvairplay.data.receiver.AirPlayServiceConnector
import pl.prodevcode.tvairplay.domain.model.HomeKitStatus
import pl.prodevcode.tvairplay.domain.repository.HomeKitRepository

/** The accessory lives in the service so Apple Home can reach it while the app is closed. */
@Singleton
class HomeKitRepositoryImpl @Inject constructor(
    private val connector: AirPlayServiceConnector,
) : HomeKitRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val status: Flow<HomeKitStatus> = connector.service.flatMapLatest { svc ->
        svc?.homeKitStatus?.map {
            HomeKitStatus(
                running = it.running, paired = it.paired, controllers = it.controllers,
                setupCode = it.setupCode, accessoryId = it.accessoryId,
            )
        } ?: flowOf(HomeKitStatus())
    }

    override fun resetPairings() { connector.service.value?.resetHomeKitPairings() }
}
