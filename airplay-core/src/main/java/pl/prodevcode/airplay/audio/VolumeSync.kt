package pl.prodevcode.airplay.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Keeps the TV's media volume and the AirPlay sender's volume in step, both ways:
 * sender → device via RAOP volume messages, device → sender via DACP volume up/down steps.
 */
class VolumeSync(
    private val context: Context,
    private val audioManager: AudioManager,
    private val dacp: () -> DacpController?,
    private val sessionActive: () -> Boolean,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var receiver: BroadcastReceiver? = null

    private var pendingEchoes = 0
    // senders park volume at -30 as route closes; session must not leave device silenced
    private var preZeroIdx = -1
    @Volatile private var senderFrac = -1f
    private var syncTarget = -1f
    private var syncHint = 0
    private var syncDir = 0
    private var syncSteps = 0
    private val syncTimeout = Runnable { _syncEnd() }

    fun start() {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) = onDeviceVolumeBroadcast(intent)
        }
        ContextCompat.registerReceiver(context, r, IntentFilter(VolumeBroadcast.ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = r
    }

    fun release() {
        receiver?.let { try { context.unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
        handler.removeCallbacks(syncTimeout)
    }

    /** RAOP volume from the sender, in dB (-30..0, -144 = mute). */
    fun onSenderVolume(volumeDb: Float) {
        val frac = if (volumeDb <= -144f) 0f else ((volumeDb + 30f) / 30f).coerceIn(0f, 1f)
        senderFrac = frac
        Log.d(TAG, "volume ${volumeDb}dB, frac $frac")
        handler.post {
            if (syncTarget >= 0f) {
                _syncStep()
                return@post
            }
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val idx = (frac * max).roundToInt()
            preZeroIdx = if (idx == 0) (if (preZeroIdx < 0) cur else preZeroIdx) else -1
            if (idx != cur) {
                pendingEchoes++
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, idx, 0)
            }
        }
    }

    /** Current device volume expressed in the sender's dB scale. */
    fun clientVolumeDb(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val db = if (vol == 0) -144f else -30f + 30f * vol / max
        Log.d(TAG, "client volume query: $vol/$max -> ${db}dB")
        return db
    }

    fun volumePercent(): Int =
        100 * audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    /** Last client gone: stop syncing and undo a sender-induced mute. */
    fun onSessionEnded() {
        senderFrac = -1f
        handler.post {
            _syncEnd()
            if (preZeroIdx >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, preZeroIdx, 0)
                preZeroIdx = -1
            }
        }
    }

    private fun onDeviceVolumeBroadcast(intent: Intent) {
        if (intent.getIntExtra(VolumeBroadcast.EXTRA_STREAM_TYPE, -1) != AudioManager.STREAM_MUSIC) return
        val idx = intent.getIntExtra(VolumeBroadcast.EXTRA_VALUE, -1)
        val prev = intent.getIntExtra(VolumeBroadcast.EXTRA_PREV_VALUE, -1)
        if (idx < 0 || prev < 0 || idx == prev) return
        if (pendingEchoes > 0) { pendingEchoes--; return }
        if (!sessionActive()) return
        val target = idx.toFloat() / audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        Log.d(TAG, "local volume $prev -> $idx, sync sender to $target")
        preZeroIdx = -1
        val active = syncTarget >= 0f
        syncTarget = target
        syncHint = if (idx > prev) 1 else -1
        syncDir = 0
        syncSteps = 0
        if (!active) _syncStep()
    }

    private fun _syncStep() {
        handler.removeCallbacks(syncTimeout)
        val target = syncTarget
        if (target < 0f) return
        val frac = senderFrac
        val want = when {
            frac < 0f -> syncHint
            abs(target - frac) <= EPS -> 0
            target > frac -> 1
            else -> -1
        }
        if (want == 0 || (syncDir != 0 && want != syncDir) || syncSteps >= MAX_STEPS) {
            _syncEnd()
            return
        }
        syncDir = want
        syncSteps++
        if (want > 0) dacp()?.volumeUp() else dacp()?.volumeDown()
        handler.postDelayed(syncTimeout, TIMEOUT_MS)
    }

    private fun _syncEnd() {
        handler.removeCallbacks(syncTimeout)
        if (syncTarget >= 0f) Log.d(TAG, "volume sync done: sender $senderFrac, target $syncTarget")
        syncTarget = -1f
        syncDir = 0
    }

    private companion object {
        const val TAG = "VolumeSync"
        const val EPS = 0.033f
        const val MAX_STEPS = 32
        const val TIMEOUT_MS = 800L
    }
}
