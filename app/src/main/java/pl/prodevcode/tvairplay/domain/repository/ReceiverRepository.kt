package pl.prodevcode.tvairplay.domain.repository

import android.view.Surface
import kotlinx.coroutines.flow.Flow
import pl.prodevcode.tvairplay.domain.model.ReceiverState

interface ReceiverRepository {
    val state: Flow<ReceiverState>

    fun start()
    fun stop()
    fun restart()

    fun attachMirrorSurface(surface: Surface)
    fun detachMirrorSurface(surface: Surface)
    fun attachVideoSurface(surface: Surface)
    fun detachVideoSurface(surface: Surface)

    fun togglePlayPause()
    fun seekBy(deltaMs: Long)
    fun seekTo(positionMs: Long)
    fun skipNext()
    fun skipPrevious()
    fun stopVideo()
}
