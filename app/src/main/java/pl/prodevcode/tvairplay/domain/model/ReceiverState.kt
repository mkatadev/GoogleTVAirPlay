package pl.prodevcode.tvairplay.domain.model

enum class ReceiverStatus { STOPPED, STARTING, RUNNING, ERROR }

/** What the connected Apple device is currently sending. */
enum class SessionMode { IDLE, CONNECTED, MIRRORING, VIDEO, AUDIO }

/** Encoded artwork bytes with content-based equality so state diffing works. */
class CoverArt(val bytes: ByteArray) {
    override fun equals(other: Any?) = other is CoverArt && bytes.contentEquals(other.bytes)
    override fun hashCode() = bytes.contentHashCode()
}

data class NowPlaying(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val coverArt: CoverArt? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playing: Boolean = false,
) {
    val hasTrack: Boolean get() = title.isNotBlank() || artist.isNotBlank()
}

data class VideoPlayback(
    val title: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val aspectRatio: Float = 16f / 9f,
)

data class ReceiverState(
    val status: ReceiverStatus = ReceiverStatus.STOPPED,
    val mode: SessionMode = SessionMode.IDLE,
    val connectedClients: Int = 0,
    val mirrorAspectRatio: Float = 16f / 9f,
    val nowPlaying: NowPlaying = NowPlaying(),
    val video: VideoPlayback = VideoPlayback(),
    val pin: String? = null,
    val lastLog: String = "",
)

data class DeviceInfo(
    val name: String,
    val ipAddress: String?,
)
