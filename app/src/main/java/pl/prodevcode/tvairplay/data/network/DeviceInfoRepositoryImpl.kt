package pl.prodevcode.tvairplay.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import pl.prodevcode.tvairplay.domain.model.DeviceInfo
import pl.prodevcode.tvairplay.domain.repository.DeviceInfoRepository
import pl.prodevcode.tvairplay.domain.repository.SettingsRepository

@Singleton
class DeviceInfoRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    settings: SettingsRepository,
) : DeviceInfoRepository {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    private val ipAddress: Flow<String?> = callbackFlow {
        fun emit(props: LinkProperties?) {
            trySend(props?.linkAddresses?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress)
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) = emit(lp)
            override fun onLost(network: Network) { trySend(null) }
        }
        emit(connectivity.activeNetwork?.let(connectivity::getLinkProperties))
        connectivity.registerNetworkCallback(
            NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
            callback,
        )
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    override val deviceInfo: Flow<DeviceInfo> =
        combine(settings.settings.map { it.deviceName }, ipAddress) { name, ip -> DeviceInfo(name, ip) }
}
