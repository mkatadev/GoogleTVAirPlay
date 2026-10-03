package pl.prodevcode.tvairplay.data.receiver

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import androidx.media3.common.C
import pl.prodevcode.airplay.Prefs
import pl.prodevcode.airplay.renderer.VideoTrack
import pl.prodevcode.airplay.service.AirPlayService
import pl.prodevcode.tvairplay.domain.model.CoverArt
import pl.prodevcode.tvairplay.domain.model.MediaTrack
import pl.prodevcode.tvairplay.domain.model.TrackKind
import pl.prodevcode.tvairplay.domain.model.NowPlaying
import pl.prodevcode.tvairplay.domain.model.ReceiverState
import pl.prodevcode.tvairplay.domain.model.ReceiverStatus
import pl.prodevcode.tvairplay.domain.model.SessionMode
import pl.prodevcode.tvairplay.domain.model.SessionModeResolver
import pl.prodevcode.tvairplay.domain.model.VideoPlayback
import pl.prodevcode.tvairplay.domain.repository.ReceiverRepository
import pl.prodevcode.tvairplay.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@Singleton
class ReceiverRepositoryImpl @Inject constructor(
    private val connector: AirPlayServiceConnector,
    private val settings: SettingsRepository,
) : ReceiverRepository {

    init { connector.bind() }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val state: Flow<ReceiverState> = connector.service.flatMapLatest { svc ->
        if (svc == null) flowOf(ReceiverState(status = ReceiverStatus.STARTING)) else svc.stateFlow()
    }

    private fun AirPlayService.stateFlow(): Flow<ReceiverState> {
        val session = combine(
            serverState, connectionCount, mirroringActive, videoPlaybackActive, audioOnly
        ) { status, clients, mirroring, video, audio ->
            Triple(status.toDomain(), clients, SessionModeResolver.resolve(clients, mirroring, video, audio))
        }
        val music = combine(trackInfo, positionMs, durationMs, playing) { track, pos, dur, playing ->
            NowPlaying(
                title = track.title, artist = track.artist, album = track.album,
                coverArt = track.coverArtBytes?.let(::CoverArt), positionMs = pos, durationMs = dur, playing = playing,
            )
        }
        val hls = combine(videoPlaybackInfo, videoTitle, videoPlaybackAspect, video.tracks) { info, title, aspect, tracks ->
            VideoPlayback(
                title = title, positionMs = info.positionMs, durationMs = info.durationMs,
                playing = info.playing, buffering = info.buffering, aspectRatio = aspect,
                audioTracks = tracks.filter { it.type == C.TRACK_TYPE_AUDIO }.map { it.toDomain(TrackKind.AUDIO) },
                subtitleTracks = tracks.filter { it.type == C.TRACK_TYPE_TEXT }.map { it.toDomain(TrackKind.SUBTITLE) },
            )
        }
        return combine(session, music, hls, videoAspect, connector.pin, connector.logs) { values ->
            @Suppress("UNCHECKED_CAST")
            val session = values[0] as Triple<ReceiverStatus, Int, SessionMode>
            val (status, clients, mode) = session
            ReceiverState(
                status = status,
                mode = mode,
                connectedClients = clients,
                mirrorAspectRatio = values[3] as Float,
                nowPlaying = values[1] as NowPlaying,
                video = values[2] as VideoPlayback,
                pin = values[4] as String?,
                lastLog = values[5] as String,
            )
        }
    }

    private fun VideoTrack.toDomain(kind: TrackKind) =
        MediaTrack(id = id, kind = kind, label = label, language = language, selected = selected)

    private fun AirPlayService.ServerState.toDomain() = when (this) {
        AirPlayService.ServerState.STOPPED -> ReceiverStatus.STOPPED
        AirPlayService.ServerState.RUNNING -> ReceiverStatus.RUNNING
        AirPlayService.ServerState.ERROR -> ReceiverStatus.ERROR
    }

    private val svc get() = connector.service.value

    override fun start() {
        val s = svc
        if (s == null) {
            connector.startForeground()
            return
        }
        if (s.serverState.value == AirPlayService.ServerState.RUNNING) return
        s.startServer(deviceName())
    }

    override fun stop() { svc?.stopServer() }

    override fun restart() {
        val s = svc ?: return
        if (s.serverState.value == AirPlayService.ServerState.RUNNING) s.stopServer()
        s.startServer(deviceName())
    }

    private fun deviceName() = runBlocking { settings.settings.first().deviceName.ifBlank { Prefs.DEF_SERVER_NAME } }

    override fun togglePlayPause() {
        val s = svc ?: return
        if (s.videoPlaybackActive.value) s.setVideoPlaying(!s.videoPlaybackInfo.value.playing)
        else s.togglePlayPause()
    }

    override fun seekBy(deltaMs: Long) {
        val s = svc ?: return
        if (s.videoPlaybackActive.value) s.seekVideoBy(deltaMs)
    }

    override fun seekTo(positionMs: Long) {
        val s = svc ?: return
        if (s.videoPlaybackActive.value) s.seekVideoTo(positionMs)
    }

    override fun skipNext() { svc?.dacpController?.nextItem() }
    override fun skipPrevious() { svc?.dacpController?.prevItem() }
    override fun stopVideo() { svc?.stopVideoPlayback() }
    override fun selectAudioTrack(id: String) { svc?.selectVideoTrack(C.TRACK_TYPE_AUDIO, id) }
    override fun selectSubtitleTrack(id: String?) { svc?.selectVideoTrack(C.TRACK_TYPE_TEXT, id) }
}
