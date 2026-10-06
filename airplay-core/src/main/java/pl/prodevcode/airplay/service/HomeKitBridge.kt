package pl.prodevcode.airplay.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import pl.prodevcode.airplay.Prefs
import pl.prodevcode.airplay.audio.VolumeBroadcast
import pl.prodevcode.homekit.HomeKitAccessoryServer
import pl.prodevcode.homekit.accessory.AccessoryInfo
import pl.prodevcode.homekit.accessory.RemoteKey
import pl.prodevcode.homekit.accessory.TelevisionControls

/**
 * Hosts the HomeKit Television accessory inside the service: receiver state flows into the
 * accessory, Home app writes come back as receiver actions on the main thread.
 */
internal class HomeKitBridge(
    private val service: AirPlayService,
    private val prefs: SharedPreferences,
    private val audioManager: AudioManager,
    scope: CoroutineScope,
) : TelevisionControls {

    private val handler = Handler(Looper.getMainLooper())
    private var volumeReceiver: BroadcastReceiver? = null

    val server = HomeKitAccessoryServer(
        service, service.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), scope,
        AccessoryInfo(
            name = serverName(),
            manufacturer = "prodevcode",
            model = Build.MODEL.ifBlank { "Google TV" },
            serialNumber = Settings.Secure.getString(service.contentResolver, Settings.Secure.ANDROID_ID) ?: "0",
            firmwareRevision = firmwareRevision(),
        ),
        controls = this,
    )

    val enabled: Boolean get() = prefs.getBoolean(Prefs.HOMEKIT_ENABLED, Prefs.DEF_HOMEKIT_ENABLED)

    init {
        scope.launch { service.serverState.collect { server.television.setActive(it == AirPlayService.ServerState.RUNNING) } }
        scope.launch {
            combine(service.connectionCount, service.audioOnly, service.playing, service.video.active, service.video.info) { clients, audio, playing, videoActive, video ->
                when {
                    videoActive -> video.playing
                    clients > 0 && audio -> playing
                    else -> null
                }
            }.distinctUntilChanged().collect { server.television.setPlaying(it) }
        }
    }

    /** Applies [Prefs.HOMEKIT_ENABLED]; the server keeps running across receiver restarts. */
    fun sync() {
        if (enabled) {
            server.setName(serverName())
            if (!server.status.value.running) {
                server.start()
                pushVolume()
                startVolumeWatch()
            }
        } else {
            stopVolumeWatch()
            server.stop()
        }
    }

    fun release() {
        stopVolumeWatch()
        server.stop()
    }

    private fun serverName() = prefs.getString(Prefs.SERVER_NAME, Prefs.DEF_SERVER_NAME)?.ifBlank { null } ?: Prefs.DEF_SERVER_NAME

    private fun firmwareRevision(): String {
        val version = runCatching { service.packageManager.getPackageInfo(service.packageName, 0).versionName }.getOrNull() ?: ""
        // HAP requires a dotted numeric revision; local builds report "dev"
        return version.takeIf { it.matches(Regex("""\d+(\.\d+)*""")) } ?: "0.0.0"
    }

    // --- receiver → accessory ------------------------------------------------------------------------

    private fun pushVolume() {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        server.television.setVolume(if (max > 0) 100 * cur / max else 0, audioManager.isStreamMute(AudioManager.STREAM_MUSIC))
    }

    private fun startVolumeWatch() {
        if (volumeReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getIntExtra(VolumeBroadcast.EXTRA_STREAM_TYPE, -1) == AudioManager.STREAM_MUSIC) pushVolume()
            }
        }
        ContextCompat.registerReceiver(service, r, IntentFilter(VolumeBroadcast.ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        volumeReceiver = r
    }

    private fun stopVolumeWatch() {
        volumeReceiver?.let { try { service.unregisterReceiver(it) } catch (_: Exception) {} }
        volumeReceiver = null
    }

    // --- TelevisionControls (HAP threads) ------------------------------------------------------------

    private fun onMain(block: () -> Unit) { handler.post(block) }

    override fun setActive(active: Boolean) = onMain {
        if (active) service.startServer(serverName()) else service.stopServer()
    }

    override fun setPlaying(playing: Boolean) = onMain {
        if (service.video.active.value) service.setVideoPlaying(playing)
        else if (service.playing.value != playing) service.togglePlayPause()
    }

    override fun remoteKey(key: RemoteKey) = onMain {
        val video = service.video.active.value
        when (key) {
            RemoteKey.PLAY_PAUSE, RemoteKey.SELECT ->
                if (video) service.setVideoPlaying(!service.video.info.value.playing) else service.togglePlayPause()
            RemoteKey.NEXT_TRACK -> if (video) service.seekVideoBy(SEEK_MS) else service.dacpController?.nextItem()
            RemoteKey.PREVIOUS_TRACK -> if (video) service.seekVideoBy(-SEEK_MS) else service.dacpController?.prevItem()
            RemoteKey.FAST_FORWARD, RemoteKey.ARROW_RIGHT -> if (video) service.seekVideoBy(SEEK_MS) else service.dacpController?.nextItem()
            RemoteKey.REWIND, RemoteKey.ARROW_LEFT -> if (video) service.seekVideoBy(-SEEK_MS) else service.dacpController?.prevItem()
            RemoteKey.BACK, RemoteKey.EXIT -> if (video) service.stopVideoPlayback()
            RemoteKey.ARROW_UP, RemoteKey.ARROW_DOWN, RemoteKey.INFORMATION -> Unit
        }
    }

    override fun setVolume(percent: Int) = onMain {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (percent.coerceIn(0, 100) * max / 100f).roundToInt(), 0)
    }

    override fun volumeStep(up: Boolean) = onMain {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
    }

    override fun setMuted(muted: Boolean) = onMain {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0)
    }

    private companion object {
        const val PREFS_NAME = "homekit"
        const val SEEK_MS = 15_000L
    }
}
