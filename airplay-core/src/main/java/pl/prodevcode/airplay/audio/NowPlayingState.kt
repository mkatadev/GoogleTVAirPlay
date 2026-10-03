package pl.prodevcode.airplay.audio

import android.graphics.BitmapFactory
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Track metadata, artwork and extrapolated playback position of the current audio session. */
class NowPlayingState {

    private val _track = MutableStateFlow(TrackInfo())
    val track = _track.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs = _durationMs.asStateFlow()

    private val _playing = MutableStateFlow(true)
    val playing = _playing.asStateFlow()

    @Volatile var coverArtBytes: ByteArray? = null
        private set
    @Volatile private var progressBaseMs = 0L
    @Volatile private var progressBaseTime = 0L

    fun currentPositionMs(): Long {
        if (progressBaseTime == 0L || !_playing.value) return _positionMs.value
        val elapsed = SystemClock.elapsedRealtime() - progressBaseTime
        return (progressBaseMs + elapsed).coerceIn(0, _durationMs.value)
    }

    fun onMetadata(dmap: ByteArray): TrackInfo {
        val info = TrackInfo.fromDmap(DmapParser.parse(dmap), _track.value.coverArt, _track.value.coverArtBytes)
        _track.value = info
        if (info.durationMs > 0) _durationMs.value = info.durationMs
        return info
    }

    /** @return false when the bytes do not decode to an image. */
    fun onCoverArt(data: ByteArray): Boolean {
        val bmp = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return false
        coverArtBytes = data
        _track.value = _track.value.copy(coverArt = bmp, coverArtBytes = data)
        return true
    }

    /** RAOP progress in 44.1 kHz sample units. @return false for the degenerate values sent around pause/resume. */
    fun onProgress(start: Long, curr: Long, end: Long): Boolean {
        val rate = 44100.0
        val posMs = ((curr - start) / rate * 1000).toLong().coerceAtLeast(0)
        val durMs = ((end - start) / rate * 1000).toLong().coerceAtLeast(0)
        if (durMs <= 0) return false
        _positionMs.value = posMs
        _durationMs.value = durMs
        progressBaseMs = posMs
        progressBaseTime = SystemClock.elapsedRealtime()
        _playing.value = true
        return true
    }

    fun setPlaying(playing: Boolean) {
        _playing.value = playing
        if (playing) {
            // resume extrapolation from current position
            progressBaseMs = _positionMs.value
            progressBaseTime = SystemClock.elapsedRealtime()
        } else {
            // freeze position
            _positionMs.value = currentPositionMs()
            progressBaseTime = 0
        }
    }

    fun clear() {
        coverArtBytes = null
        _track.value = TrackInfo()
        _positionMs.value = 0
        _durationMs.value = 0
    }
}
