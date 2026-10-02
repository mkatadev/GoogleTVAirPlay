package pl.prodevcode.tvairplay.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import pl.prodevcode.airplay.Prefs
import pl.prodevcode.tvairplay.domain.model.ReceiverSettings
import pl.prodevcode.tvairplay.domain.repository.SettingsRepository

/**
 * Backed by the same SharedPreferences file the AirPlay core reads (`Prefs.NAME`), so the
 * receiver and the UI never disagree about configuration.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

    init {
        // TV-only defaults: fixed landscape panel, no portrait/custom resolution handling
        prefs.edit {
            if (!prefs.contains(Prefs.RESOLUTION)) putString(Prefs.RESOLUTION, "landscape")
            if (!prefs.contains(Prefs.SERVER_NAME)) putString(Prefs.SERVER_NAME, DEFAULT_NAME)
        }
    }

    override val settings: Flow<ReceiverSettings> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        trySend(read())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    override suspend fun update(transform: (ReceiverSettings) -> ReceiverSettings) =
        withContext(Dispatchers.IO) { write(transform(read())) }

    private fun read() = ReceiverSettings(
        deviceName = prefs.getString(Prefs.SERVER_NAME, null) ?: DEFAULT_NAME,
        startOnBoot = prefs.getBoolean(Prefs.BOOT_AUTO_START, Prefs.DEF_BOOT_AUTO_START),
        runInBackground = prefs.getBoolean(Prefs.RUN_IN_BACKGROUND, Prefs.DEF_RUN_IN_BACKGROUND),
        openAppOnConnect = prefs.getBoolean(Prefs.LAUNCH_ON_CONNECT, Prefs.DEF_LAUNCH_ON_CONNECT),
        requirePin = prefs.getBoolean(Prefs.REQUIRE_PIN, Prefs.DEF_REQUIRE_PIN),
        hevcEnabled = prefs.getBoolean(Prefs.H265_ENABLED, Prefs.DEF_H265_ENABLED),
        advertiseVideo = prefs.getBoolean(Prefs.ADVERTISE_VIDEO, Prefs.DEF_ADVERTISE_VIDEO),
        advertiseAudio = prefs.getBoolean(Prefs.ADVERTISE_AUDIO, Prefs.DEF_ADVERTISE_AUDIO),
    )

    private fun write(s: ReceiverSettings) = prefs.edit {
        putString(Prefs.SERVER_NAME, s.deviceName)
        putBoolean(Prefs.BOOT_AUTO_START, s.startOnBoot)
        putBoolean(Prefs.RUN_IN_BACKGROUND, s.runInBackground)
        putBoolean(Prefs.LAUNCH_ON_CONNECT, s.openAppOnConnect)
        putBoolean(Prefs.REQUIRE_PIN, s.requirePin)
        putBoolean(Prefs.H265_ENABLED, s.hevcEnabled)
        putBoolean(Prefs.ADVERTISE_VIDEO, s.advertiseVideo)
        putBoolean(Prefs.ADVERTISE_AUDIO, s.advertiseAudio)
    }

    private companion object {
        const val DEFAULT_NAME = "Google TV"
    }
}
