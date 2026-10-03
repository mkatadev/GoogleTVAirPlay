package pl.prodevcode.tvairplay.data.diagnostics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import pl.prodevcode.airplay.discovery.NsdServiceManager
import pl.prodevcode.airplay.service.AirPlayService
import pl.prodevcode.tvairplay.data.receiver.AirPlayServiceConnector
import pl.prodevcode.tvairplay.domain.model.AdvertisingDiagnostics
import pl.prodevcode.tvairplay.domain.model.AdvertisingState
import pl.prodevcode.tvairplay.domain.model.Diagnostics
import pl.prodevcode.tvairplay.domain.model.NetworkDiagnostics
import pl.prodevcode.tvairplay.domain.model.ReceiverStatus
import pl.prodevcode.tvairplay.domain.model.SessionDiagnostics
import pl.prodevcode.tvairplay.domain.repository.DeviceInfoRepository
import pl.prodevcode.tvairplay.domain.repository.DiagnosticsRepository
import pl.prodevcode.tvairplay.domain.repository.ReceiverRepository

@Singleton
class DiagnosticsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val connector: AirPlayServiceConnector,
    private val receiver: ReceiverRepository,
    private val deviceInfo: DeviceInfoRepository,
) : DiagnosticsRepository {

    private val appVersion: String by lazy {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val diagnostics: Flow<Diagnostics> = connector.service.flatMapLatest { svc ->
        if (svc == null) flowOf(Diagnostics(appVersion = appVersion, receiverStatus = ReceiverStatus.STARTING))
        else svc.diagnosticsFlow()
    }

    private fun AirPlayService.diagnosticsFlow(): Flow<Diagnostics> {
        // codec/fps counters are plain fields on the renderers; sample them while someone is looking
        val session = flow {
            while (true) {
                val d = collectDebugInfo()
                emit(
                    SessionDiagnostics(
                        videoCodec = d.videoCodec, videoResolution = d.videoRes, videoFps = d.videoFps,
                        droppedFrames = d.droppedFrames, audioCodec = d.audioCodec,
                        audioUnderruns = d.audio?.underruns ?: 0,
                    )
                )
                delay(SAMPLE_MS)
            }
        }
        val core = combine(serverState, port, lastError, networkStatus, nsdStatus) { status, port, err, net, nsd ->
            Diagnostics(
                appVersion = appVersion,
                receiverStatus = status.toDomain(),
                port = port,
                lastError = err,
                network = NetworkDiagnostics(net.transport, net.interfaceName, net.addresses, net.changes),
                advertising = AdvertisingDiagnostics(
                    raop = nsd.raop.toDomain(), airplay = nsd.airplay.toDomain(),
                    raopError = nsd.raopError, airplayError = nsd.airplayError, announcements = nsd.registrations,
                ),
            )
        }
        return combine(core, receiver.state, deviceInfo.deviceInfo, session) { base, rs, dev, sess ->
            base.copy(
                deviceName = dev.name,
                lastLog = rs.lastLog,
                session = sess.copy(connectedClients = rs.connectedClients, mode = rs.mode),
            )
        }
    }

    private fun AirPlayService.ServerState.toDomain() = when (this) {
        AirPlayService.ServerState.STOPPED -> ReceiverStatus.STOPPED
        AirPlayService.ServerState.RUNNING -> ReceiverStatus.RUNNING
        AirPlayService.ServerState.ERROR -> ReceiverStatus.ERROR
    }

    private fun NsdServiceManager.State.toDomain() = when (this) {
        NsdServiceManager.State.IDLE -> AdvertisingState.IDLE
        NsdServiceManager.State.PENDING -> AdvertisingState.PENDING
        NsdServiceManager.State.REGISTERED -> AdvertisingState.REGISTERED
        NsdServiceManager.State.FAILED -> AdvertisingState.FAILED
    }

    private companion object { const val SAMPLE_MS = 1_000L }
}
