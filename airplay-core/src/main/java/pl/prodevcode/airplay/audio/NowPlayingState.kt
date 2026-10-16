package pl.prodevcode.airplay.audio

import android.graphics.BitmapFactory
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Track metadata, artwork and extrapolated playback position of the current audio session. */
class NowPlayingState(
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {

    @Volatile var sessionActive = false
        private set

    @Synchronized
    fun beginSession() {
        sessionActive = true
    }

    @Synchronized
    fun endSession() {
        sessionActive = false
        clear()
    }

    private val _track = MutableStateFlow(TrackInfo())
    val track = _track.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs = _durationMs.asStateFlow()

    private val _playing = MutableStateFlow(false)
    val playing = _playing.asStateFlow()

    @Volatile var coverArtBytes: ByteArray? = null
        private set
    @Volatile private var progressBaseMs = 0L
    @Volatile private var progressBaseTime = 0L

    fun currentPositionMs(): Long {
        if (progressBaseTime == 0L || !_playing.value) return _positionMs.value
        val elapsed = elapsedRealtime() - progressBaseTime
        return (progressBaseMs + elapsed).coerceIn(0, _durationMs.value)
    }

    @Synchronized
    fun onMetadata(dmap: ByteArray): TrackInfo? {
        if (!sessionActive) return null
        val info = TrackInfo.fromDmap(DmapParser.parse(dmap), _track.value.coverArt, _track.value.coverArtBytes)
        _track.value = info
        if (info.durationMs > 0) _durationMs.value = info.durationMs
        return info
    }

    /** @return false when the bytes do not decode to an image. */
    @Synchronized
    fun onCoverArt(data: ByteArray): Boolean {
        if (!sessionActive) return false
        val bmp = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return false
        coverArtBytes = data
        _track.value = _track.value.copy(coverArt = bmp, coverArtBytes = data)
        return true
    }

    /** RAOP progress in 44.1 kHz sample units. @return false for the degenerate values sent around pause/resume. */
    @Synchronized
    fun onProgress(start: Long, curr: Long, end: Long): Boolean {
        if (!sessionActive) return false
        val rate = 44100.0
        val posMs = ((curr - start) / rate * 1000).toLong().coerceAtLeast(0)
        val durMs = ((end - start) / rate * 1000).toLong().coerceAtLeast(0)
        if (durMs <= 0) return false
        _positionMs.value = posMs
        _durationMs.value = durMs
        progressBaseMs = posMs
        progressBaseTime = elapsedRealtime()
        _playing.value = true
        return true
    }

    @Synchronized
    fun setPlaying(playing: Boolean) {
        if (!sessionActive) return
        _playing.value = playing
        if (playing) {
            // resume extrapolation from current position
            progressBaseMs = _positionMs.value
            progressBaseTime = elapsedRealtime()
        } else {
            // freeze position
            _positionMs.value = currentPositionMs()
            progressBaseTime = 0
        }
    }

    @Synchronized
    fun clear() {
        coverArtBytes = null
        _track.value = TrackInfo()
        _positionMs.value = 0
        _durationMs.value = 0
        _playing.value = false
        progressBaseMs = 0
        progressBaseTime = 0
    }
}
