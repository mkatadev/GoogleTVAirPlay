package pl.prodevcode.airplay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import pl.prodevcode.airplay.R
import pl.prodevcode.airplay.audio.TrackInfo

/** Foreground notification of the receiver: idle / PIN prompt / media transport. */
internal class ServiceNotifications(
    private val service: Service,
    private val snapshot: () -> Snapshot,
) {
    data class Snapshot(
        val track: TrackInfo,
        val audioOnly: Boolean,
        val pin: String?,
        val session: MediaSession?,
    )

    var foreground = false
        private set

    private val manager get() = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, service.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    fun promoteToForeground() {
        ServiceCompat.startForeground(service, NOTIFICATION_ID, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        foreground = true
    }

    fun update() = manager.notify(NOTIFICATION_ID, build())

    fun dismiss() {
        if (!foreground) return
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foreground = false
    }

    // library stays decoupled from the host app: resolve its leanback/launcher activity at runtime
    fun launcherIntent(): Intent =
        service.packageManager.getLeanbackLaunchIntentForPackage(service.packageName)
            ?: service.packageManager.getLaunchIntentForPackage(service.packageName)
            ?: Intent()

    private fun build(): Notification {
        val s = snapshot()
        val pi = PendingIntent.getActivity(service, 0, launcherIntent(), PendingIntent.FLAG_IMMUTABLE)
        val isAudio = s.audioOnly && s.track.title.isNotEmpty()

        val builder = NotificationCompat.Builder(service, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)

        if (isAudio) {
            builder.setContentTitle(s.track.title).setContentText(s.track.artist).setSubText(s.track.album)
            s.track.coverArt?.let { builder.setLargeIcon(it) }
            s.session?.let { session ->
                builder.setStyle(
                    MediaStyleNotificationHelper.MediaStyle(session).setShowActionsInCompactView(0, 1, 2)
                )
                builder.addAction(android.R.drawable.ic_media_previous, "Prev", action(AirPlayService.ACTION_PREV))
                builder.addAction(android.R.drawable.ic_media_pause, "Pause", action(AirPlayService.ACTION_PLAY_PAUSE))
                builder.addAction(android.R.drawable.ic_media_next, "Next", action(AirPlayService.ACTION_NEXT))
            }
        } else if (s.pin != null) {
            // passive handoff only: do not launch/reorder the activity during pin auth
            builder.setContentTitle(service.getString(R.string.notification_pin_title))
                .setContentText(service.getString(R.string.notification_pin_text, s.pin))
        } else {
            builder.setContentTitle(service.getString(R.string.notification_title))
                .setContentText(service.getString(R.string.notification_text))
        }
        return builder.build()
    }

    private fun action(action: String): PendingIntent {
        val intent = Intent(action).setPackage(service.packageName)
        return PendingIntent.getBroadcast(
            service, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val CHANNEL_ID = "airplay_service"
        const val NOTIFICATION_ID = 1
    }
}
