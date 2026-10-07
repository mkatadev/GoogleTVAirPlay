package pl.prodevcode.tvairplay.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.prodevcode.tvairplay.domain.model.ReceiverState

interface ReceiverRepository {
    val state: Flow<ReceiverState>

    fun start()
    fun stop()
    fun restart()
    /** The user left the app while a session was active; do not pull the UI back for that session. */
    fun uiDismissed()

    fun togglePlayPause()
    fun seekBy(deltaMs: Long)
    fun seekTo(positionMs: Long)
    fun skipNext()
    fun skipPrevious()
    fun stopVideo()
    /** Ends the current AirPlay session (music or mirroring); the receiver keeps running. */
    fun stopSharing()
    /** Hide the PIN prompt; the PIN stays valid until it expires. */
    fun dismissPin()
    fun selectAudioTrack(id: String)
    /** `null` turns subtitles off. */
    fun selectSubtitleTrack(id: String?)
}
