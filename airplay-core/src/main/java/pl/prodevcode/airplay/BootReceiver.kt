package pl.prodevcode.airplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import pl.prodevcode.airplay.service.AirPlayService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val autoStart = prefs.getBoolean(Prefs.BOOT_AUTO_START, Prefs.DEF_BOOT_AUTO_START)
        val homeKit = prefs.getBoolean(Prefs.HOMEKIT_ENABLED, Prefs.DEF_HOMEKIT_ENABLED)
        if (!autoStart && !homeKit) return

        // with HomeKit alone the receiver stays off until Apple Home switches it on
        val serviceIntent = Intent(context, AirPlayService::class.java)
            .setAction(if (autoStart) AirPlayService.ACTION_START_SERVER else AirPlayService.ACTION_START_HOMEKIT)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
