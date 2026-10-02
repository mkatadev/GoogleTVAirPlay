package pl.prodevcode.tvairplay.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pl.prodevcode.tvairplay.data.licenses.AssetLicensesRepository
import pl.prodevcode.tvairplay.data.network.DeviceInfoRepositoryImpl
import pl.prodevcode.tvairplay.data.permission.OverlayPermissionRepositoryImpl
import pl.prodevcode.tvairplay.data.receiver.ReceiverRepositoryImpl
import pl.prodevcode.tvairplay.data.settings.SettingsRepositoryImpl
import pl.prodevcode.tvairplay.domain.repository.DeviceInfoRepository
import pl.prodevcode.tvairplay.domain.repository.LicensesRepository
import pl.prodevcode.tvairplay.domain.repository.OverlayPermissionRepository
import pl.prodevcode.tvairplay.domain.repository.ReceiverRepository
import pl.prodevcode.tvairplay.domain.repository.SettingsRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds @Singleton
    abstract fun bindReceiverRepository(impl: ReceiverRepositoryImpl): ReceiverRepository

    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds @Singleton
    abstract fun bindDeviceInfoRepository(impl: DeviceInfoRepositoryImpl): DeviceInfoRepository

    @Binds @Singleton
    abstract fun bindLicensesRepository(impl: AssetLicensesRepository): LicensesRepository

    @Binds @Singleton
    abstract fun bindOverlayPermissionRepository(impl: OverlayPermissionRepositoryImpl): OverlayPermissionRepository
}
