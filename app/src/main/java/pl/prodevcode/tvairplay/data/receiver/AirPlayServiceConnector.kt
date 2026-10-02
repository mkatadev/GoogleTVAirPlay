package pl.prodevcode.tvairplay.data.receiver

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.prodevcode.airplay.service.AirPlayService

/**
 * Keeps a process-wide binding to the foreground [AirPlayService] so the receiver survives
 * activity recreation and keeps running in the background.
 */
@Singleton
class AirPlayServiceConnector @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val _service = MutableStateFlow<AirPlayService?>(null)
    val service: StateFlow<AirPlayService?> = _service.asStateFlow()

    private val _logs = MutableStateFlow("")
    val logs: StateFlow<String> = _logs.asStateFlow()

    private val _pin = MutableStateFlow<String?>(null)
    val pin: StateFlow<String?> = _pin.asStateFlow()

    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as? AirPlayService.LocalBinder)?.service ?: return
            svc.logCallback = { _logs.value = it }
            svc.pinCallback = { _pin.value = it }
            _service.value = svc
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _service.value = null
        }
    }

    fun bind() {
        if (bound) return
        bound = context.bindService(
            Intent(context, AirPlayService::class.java), connection, Context.BIND_AUTO_CREATE
        )
    }

    fun startForeground() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, AirPlayService::class.java).setAction(AirPlayService.ACTION_START_SERVER)
        )
    }
}
