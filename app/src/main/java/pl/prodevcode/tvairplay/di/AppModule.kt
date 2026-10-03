package pl.prodevcode.tvairplay.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pl.prodevcode.tvairplay.data.diagnostics.DiagnosticsRepositoryImpl
import pl.prodevcode.tvairplay.data.licenses.AssetLicensesRepository
import pl.prodevcode.tvairplay.data.network.DeviceInfoRepositoryImpl
import pl.prodevcode.tvairplay.data.permission.OverlayPermissionRepositoryImpl
import pl.prodevcode.tvairplay.data.receiver.ReceiverRepositoryImpl
import pl.prodevcode.tvairplay.data.receiver.ServiceSubtitleCues
import pl.prodevcode.tvairplay.data.receiver.ServiceVideoSurfaceHost
import pl.prodevcode.tvairplay.data.security.TrustedDevicesRepositoryImpl
import pl.prodevcode.tvairplay.data.settings.AppCompatAppLocale
import pl.prodevcode.tvairplay.data.settings.SettingsRepositoryImpl
import pl.prodevcode.tvairplay.data.update.GitHubUpdateRepository
import pl.prodevcode.tvairplay.domain.repository.DeviceInfoRepository
import pl.prodevcode.tvairplay.domain.repository.DiagnosticsRepository
import pl.prodevcode.tvairplay.domain.repository.LicensesRepository
import pl.prodevcode.tvairplay.domain.repository.OverlayPermissionRepository
import pl.prodevcode.tvairplay.domain.repository.ReceiverRepository
import pl.prodevcode.tvairplay.domain.repository.SettingsRepository
import pl.prodevcode.tvairplay.domain.repository.TrustedDevicesRepository
import pl.prodevcode.tvairplay.domain.repository.UpdateRepository
import pl.prodevcode.tvairplay.platform.AppLocale
import pl.prodevcode.tvairplay.platform.SubtitleCues
import pl.prodevcode.tvairplay.platform.VideoSurfaceHost

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

    @Binds @Singleton
    abstract fun bindVideoSurfaceHost(impl: ServiceVideoSurfaceHost): VideoSurfaceHost

    @Binds @Singleton
    abstract fun bindDiagnosticsRepository(impl: DiagnosticsRepositoryImpl): DiagnosticsRepository

    @Binds @Singleton
    abstract fun bindTrustedDevicesRepository(impl: TrustedDevicesRepositoryImpl): TrustedDevicesRepository

    @Binds @Singleton
    abstract fun bindUpdateRepository(impl: GitHubUpdateRepository): UpdateRepository

    @Binds @Singleton
    abstract fun bindSubtitleCues(impl: ServiceSubtitleCues): SubtitleCues

    @Binds @Singleton
    abstract fun bindAppLocale(impl: AppCompatAppLocale): AppLocale
}
