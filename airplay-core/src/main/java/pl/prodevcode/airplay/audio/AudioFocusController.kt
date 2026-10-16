package pl.prodevcode.airplay.audio

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler

internal interface AudioFocusAccess {
    fun request(onLoss: () -> Unit): Boolean
    fun abandon()
}

internal class AudioFocusController(
    private val access: AudioFocusAccess,
    private val onInterrupted: (String) -> Unit,
) {
    private var requested = false
    private var generation = 0

    fun update(active: Boolean) {
        if (!active) {
            release()
        } else if (!requested) {
            requested = true
            val currentGeneration = ++generation
            val granted = access.request {
                if (requested && generation == currentGeneration) {
                    release()
                    onInterrupted("Audio focus lost")
                }
            }
            if (!granted) {
                release()
                onInterrupted("Audio focus request denied")
            }
        }
    }

    fun release() {
        if (!requested) return
        requested = false
        access.abandon()
    }
}

internal class AndroidAudioFocusAccess(
    private val audioManager: AudioManager,
    private val handler: Handler,
) : AudioFocusAccess {
    private var request: AudioFocusRequest? = null

    override fun request(onLoss: () -> Unit): Boolean {
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener({ change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    onLoss()
                }
            }, handler)
            .build()
        request = focusRequest
        return audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun abandon() {
        request?.let(audioManager::abandonAudioFocusRequest)
        request = null
    }
}
