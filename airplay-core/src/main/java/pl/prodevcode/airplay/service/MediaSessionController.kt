package pl.prodevcode.airplay.service

import android.app.PendingIntent
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaSession

/**
 * One Media3 [MediaSession] for the receiver. Media keys and the TV's now-playing surface talk
 * to whichever [Player] is active: the sender's audio session ([pl.prodevcode.airplay.audio.DacpPlayer])
 * or the ExoPlayer that plays AirPlay Video. When neither is active the session holds the idle
 * audio player, which advertises no commands.
 */
internal class MediaSessionController(
    context: Context,
    private val idlePlayer: Player,
    sessionActivity: PendingIntent?,
) {
    val session: MediaSession = MediaSession.Builder(context, idlePlayer)
        .setId("AirPlay")
        .apply { sessionActivity?.let { setSessionActivity(it) } }
        .build()

    /** Route the session to the video player while AirPlay Video plays; `null` returns to audio. */
    fun attachVideoPlayer(player: Player?) {
        val target = player ?: idlePlayer
        if (session.player !== target) session.player = target
    }

    fun release() {
        session.release()
        idlePlayer.release()
    }
}
