package pl.prodevcode.airplay.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lets Apple Home drive the Google TV UI itself, not only AirPlay playback: D-pad, Select, Back and
 * Home through accessibility global actions and sleep through [GLOBAL_ACTION_LOCK_SCREEN]. The user
 * enables it once in Settings → Accessibility. The only event it listens to is the window-state
 * change, and only for the package name of the app in front (the current HomeKit input); it never
 * reads window content. While it is not connected the HomeKit remote falls back to playback-only control.
 */
class TvRemoteService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        _connected.value = true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        // ignore transient system windows (volume, notifications); the app in front keeps being the input
        if (pkg == "android" || pkg.startsWith("com.android.systemui")) return
        _foregroundPackage.value = pkg
    }
    override fun onInterrupt() = Unit

    private fun disconnect() {
        if (instance === this) {
            instance = null
            _connected.value = false
        }
    }

    enum class Action { UP, DOWN, LEFT, RIGHT, SELECT, BACK, HOME, SLEEP }

    companion object {
        @Volatile private var instance: TvRemoteService? = null
        private val _connected = MutableStateFlow(false)
        private val _foregroundPackage = MutableStateFlow<String?>(null)

        /** True while the user has the service enabled, i.e. Google TV navigation and sleep are available. */
        val connected = _connected.asStateFlow()

        /** Package of the app whose window is in front; `null` until the first event. */
        val foregroundPackage = _foregroundPackage.asStateFlow()

        /** Performs [action] on the current UI; false when the service is off or the OS lacks the action. */
        fun perform(action: Action): Boolean {
            val service = instance ?: return false
            val global = when (action) {
                Action.BACK -> GLOBAL_ACTION_BACK
                Action.HOME -> GLOBAL_ACTION_HOME
                Action.SLEEP -> GLOBAL_ACTION_LOCK_SCREEN
                // D-pad global actions exist since Android 13
                Action.UP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) GLOBAL_ACTION_DPAD_UP else return false
                Action.DOWN -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) GLOBAL_ACTION_DPAD_DOWN else return false
                Action.LEFT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) GLOBAL_ACTION_DPAD_LEFT else return false
                Action.RIGHT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) GLOBAL_ACTION_DPAD_RIGHT else return false
                Action.SELECT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) GLOBAL_ACTION_DPAD_CENTER else return false
            }
            return service.performGlobalAction(global)
        }
    }
}
