package pl.prodevcode.airplay.service

import android.content.Context
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import kotlin.math.abs
import pl.prodevcode.airplay.audio.TrackInfo

/** Owns the [MediaSessionCompat] so system media keys and the TV's now-playing surface reach the receiver. */
internal class MediaSessionController(context: Context, callback: MediaSessionCompat.Callback) {

    private val session = MediaSessionCompat(context, "AirPlay").apply { setCallback(callback) }
    val token: MediaSessionCompat.Token get() = session.sessionToken

    private var lastVideoRate = -1f
    private var lastVideoPosMs = 0L
    private var lastVideoAtMs = 0L

    var active: Boolean
        get() = session.isActive
        set(value) { session.isActive = value }

    fun setMetadata(info: TrackInfo, durationMs: Long) {
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, info.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, info.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, info.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
        info.coverArt?.let { builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) }
        session.setMetadata(builder.build())
    }

    fun setAudioState(playing: Boolean, positionMs: Long) {
        val state = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(
                if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                positionMs, if (playing) 1f else 0f, SystemClock.elapsedRealtime(),
            )
            .build()
        session.setPlaybackState(state)
    }

    // consumers extrapolate position from (position, speed, updateTime): push only discontinuities
    fun setVideoState(positionSeconds: Float, rate: Float) {
        val posMs = (positionSeconds * 1000).toLong()
        val now = SystemClock.elapsedRealtime()
        val expectedMs = lastVideoPosMs +
            if (lastVideoRate > 0f) ((now - lastVideoAtMs) * lastVideoRate).toLong() else 0L
        if (rate == lastVideoRate && abs(posMs - expectedMs) < 1000) return
        lastVideoRate = rate
        lastVideoPosMs = posMs
        lastVideoAtMs = now
        val state = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_FAST_FORWARD or
                    PlaybackStateCompat.ACTION_REWIND or
                    PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                if (rate > 0f) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                posMs, rate, now,
            )
            .build()
        session.setPlaybackState(state)
    }

    fun release() = session.release()
}
