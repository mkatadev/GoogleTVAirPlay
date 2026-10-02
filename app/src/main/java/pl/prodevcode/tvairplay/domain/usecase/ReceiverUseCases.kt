package pl.prodevcode.tvairplay.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import pl.prodevcode.tvairplay.domain.model.ReceiverState
import pl.prodevcode.tvairplay.domain.model.ReceiverStatus
import pl.prodevcode.tvairplay.domain.repository.ReceiverRepository

class ObserveReceiverStateUseCase @Inject constructor(private val repo: ReceiverRepository) {
    operator fun invoke(): Flow<ReceiverState> = repo.state
}

class StartReceiverUseCase @Inject constructor(private val repo: ReceiverRepository) {
    operator fun invoke() = repo.start()
}

class StopReceiverUseCase @Inject constructor(private val repo: ReceiverRepository) {
    operator fun invoke() = repo.stop()
}

class ToggleReceiverUseCase @Inject constructor(private val repo: ReceiverRepository) {
    operator fun invoke(current: ReceiverStatus) =
        if (current == ReceiverStatus.RUNNING) repo.stop() else repo.start()
}

class RestartReceiverUseCase @Inject constructor(private val repo: ReceiverRepository) {
    operator fun invoke() = repo.restart()
}

/** Transport controls: routed to AirPlay video (HLS) or to the sender via DACP depending on session. */
class PlaybackControlUseCase @Inject constructor(private val repo: ReceiverRepository) {
    fun togglePlayPause() = repo.togglePlayPause()
    fun seekBy(deltaMs: Long) = repo.seekBy(deltaMs)
    fun seekTo(positionMs: Long) = repo.seekTo(positionMs)
    fun next() = repo.skipNext()
    fun previous() = repo.skipPrevious()
    fun stopVideo() = repo.stopVideo()
}
