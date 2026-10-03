package pl.prodevcode.airplay.service

import android.content.Context
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.media3.common.text.Cue
import pl.prodevcode.airplay.renderer.AirPlayVideoPlayer
import pl.prodevcode.airplay.renderer.VideoTrack
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

    private val _active = MutableStateFlow(false)
    val active = _active.asStateFlow()

    private val _info = MutableStateFlow(VideoPlaybackInfo())
    val info = _info.asStateFlow()

    private val _aspect = MutableStateFlow(16f / 9f)
    val aspect = _aspect.asStateFlow()

    // container/manifest metadata
    private val _title = MutableStateFlow("")
    val title = _title.asStateFlow()

    private val _tracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val tracks = _tracks.asStateFlow()

    private val _cues = MutableStateFlow<List<Cue>>(emptyList())
    val cues = _cues.asStateFlow()

    fun bind(onEnded: () -> Unit, onPlaybackInfo: (PlaybackSnapshot) -> Unit) {
        player.onVideoSize = { _, _, aspect -> _aspect.value = aspect }
        player.onTitle = { _title.value = it ?: "" }
        player.onTracksChanged = { _tracks.value = it }
        player.onCues = { _cues.value = it }
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

    fun play(location: String, startPositionSeconds: Float) {
        _info.value = VideoPlaybackInfo(positionMs = (startPositionSeconds * 1000).toLong())
        _aspect.value = 16f / 9f
        _title.value = ""
        _active.value = true
        player.play(location, startPositionSeconds)
    }

    /** @return false if no session was active. */
    fun end(): Boolean {
        if (!_active.value) return false
        _active.value = false
        player.stop()
        return true
    }

    fun reset() {
        player.stop()
        _active.value = false
        _info.value = VideoPlaybackInfo()
    }

    fun selectTrack(type: Int, id: String?) { player.selectTrack(type, id) }
    fun setSurface(surface: Surface) { player.setSurface(surface) }
    fun clearSurface(surface: Surface) { player.clearSurface(surface) }
    fun setPlaying(playing: Boolean) { player.setPlaying(playing) }
    fun scrub(positionSeconds: Float) { player.scrub(positionSeconds) }
    fun seekBy(deltaMs: Long) { player.seekBy(deltaMs) }
    fun setRate(rate: Float) { player.setRate(rate) }
}
