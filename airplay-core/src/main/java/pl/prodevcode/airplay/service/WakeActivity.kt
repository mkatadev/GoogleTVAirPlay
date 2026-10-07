package pl.prodevcode.airplay.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * Invisible activity whose only job is to wake the display: `setTurnScreenOn` is the one
 * wake path open to normal apps on Android 14+ (screen wake locks need the TURN_SCREEN_ON
 * app-op, which TV builds deny by default). It finishes as soon as it is resumed, leaving
 * whatever was on screen before the TV went to sleep.
 */
class WakeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
    }

    override fun onResume() {
        super.onResume()
        // give the power manager a moment to act on the flag before the window goes away
        Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) finish() }, FINISH_DELAY_MS)
    }

    companion object {
        private const val FINISH_DELAY_MS = 500L

        fun wake(context: Context) {
            context.startActivity(
                Intent(context, WakeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS),
            )
        }
    }
}
