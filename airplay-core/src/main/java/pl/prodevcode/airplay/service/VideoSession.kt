package pl.prodevcode.airplay.service

import android.content.Context
import android.os.SystemClock
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.prodevcode.airplay.renderer.AirPlayVideoPlayer
import pl.prodevcode.airplay.renderer.PlaybackSnapshot

data class VideoPlaybackInfo(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playing: Boolean = true,
    val speed: Float = 1f,
    val skipSilence: Boolean = false,
    val buffering: Boolean = false,
)

/** State of an AirPlay Video (HLS) session played by the TV itself via [AirPlayVideoPlayer]. */
class VideoSession internal constructor(context: Context) {

    val player by lazy { AirPlayVideoPlayer(context) }

    // hls urls point at the native httpd, valid only while the session lives
    private val _location = MutableStateFlow<String?>(null)
    val location = _location.asStateFlow()

    private val _active = MutableStateFlow(false)
    val active = _active.asStateFlow()

    private val _info = MutableStateFlow(VideoPlaybackInfo())
    val info = _info.asStateFlow()

    // bumped per /play so the ui resets transport state on back-to-back plays too
    private val _playSeq = MutableStateFlow(0L)
    val playSeq = _playSeq.asStateFlow()

    private val _aspect = MutableStateFlow(16f / 9f)
    val aspect = _aspect.asStateFlow()

    private val _size = MutableStateFlow<Pair<Int, Int>?>(null)
    val size = _size.asStateFlow()

    // container/manifest metadata
    private val _title = MutableStateFlow("")
    val title = _title.asStateFlow()

    // recent /playback-info polls with no playback = pending video; polls start ~1s before /play
    @Volatile private var lastPollAt = 0L
    @Volatile private var pollSuppressed = false

    fun bind(onEnded: () -> Unit, onPlaybackInfo: (PlaybackSnapshot) -> Unit) {
        player.onVideoSize = { width, height, aspect ->
            _aspect.value = aspect
            _size.value = width to height
        }
        player.onTitle = { _title.value = it ?: "" }
        player.onEnded = onEnded
        player.onPlaybackInfo = { snapshot ->
            onPlaybackInfo(snapshot)
            if (_active.value) {
                _info.value = VideoPlaybackInfo(
                    positionMs = (snapshot.position * 1000).toLong(),
                    durationMs = if (snapshot.duration > 0f) (snapshot.duration * 1000).toLong() else 0L,
                    playing = snapshot.playWhenReady,
                    speed = snapshot.speed,
                    skipSilence = snapshot.skipSilence,
                    buffering = snapshot.buffering,
                )
            }
        }
    }

    fun onPoll() { lastPollAt = SystemClock.elapsedRealtime() }

    /** A sender is about to /play: polls arrived recently and no other session type is running. */
    fun pending(otherSessionActive: Boolean): Boolean =
        !_active.value && !otherSessionActive && !pollSuppressed &&
            SystemClock.elapsedRealtime() - lastPollAt < POLL_PENDING_TIMEOUT_MS

    fun play(location: String, startPositionSeconds: Float) {
        _location.value = location
        _playSeq.value++
        pollSuppressed = false
        _info.value = VideoPlaybackInfo(positionMs = (startPositionSeconds * 1000).toLong())
        _aspect.value = 16f / 9f
        _size.value = null
        _title.value = ""
        _active.value = true
        player.play(location, startPositionSeconds)
    }

    /** @return false if no session was active. */
    fun end(): Boolean {
        if (!_active.value) return false
        // lingering polls after a stop must not bounce the UI back to a pending session
        pollSuppressed = true
        _active.value = false
        player.stop()
        return true
    }

    fun reset() {
        player.stop()
        _active.value = false
        _info.value = VideoPlaybackInfo()
        lastPollAt = 0
        pollSuppressed = false
    }

    fun onAllClientsGone() {
        lastPollAt = 0
        pollSuppressed = false
    }

    fun setSurface(surface: Surface) { player.setSurface(surface) }
    fun clearSurface(surface: Surface) { player.clearSurface(surface) }
    fun setPlaying(playing: Boolean) { player.setPlaying(playing) }
    fun scrub(positionSeconds: Float) { player.scrub(positionSeconds) }
    fun seekBy(deltaMs: Long) { player.seekBy(deltaMs) }
    fun setRate(rate: Float) { player.setRate(rate) }
    fun setScrubbing(enabled: Boolean) { player.setScrubbing(enabled) }
    fun setSpeed(speed: Float) { player.setSpeed(speed) }
    fun setSkipSilence(enabled: Boolean) { player.setSkipSilence(enabled) }

    private companion object { const val POLL_PENDING_TIMEOUT_MS = 3_000L }
}
