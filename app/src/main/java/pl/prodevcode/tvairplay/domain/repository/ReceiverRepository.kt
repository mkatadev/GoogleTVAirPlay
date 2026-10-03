package pl.prodevcode.tvairplay.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.prodevcode.tvairplay.domain.model.ReceiverState

interface ReceiverRepository {
    val state: Flow<ReceiverState>

    fun start()
    fun stop()
    fun restart()

    fun togglePlayPause()
    fun seekBy(deltaMs: Long)
    fun seekTo(positionMs: Long)
    fun skipNext()
    fun skipPrevious()
    fun stopVideo()
    /** Hide the PIN prompt; the PIN stays valid until it expires. */
    fun dismissPin()
    fun selectAudioTrack(id: String)
    /** `null` turns subtitles off. */
    fun selectSubtitleTrack(id: String?)
}
