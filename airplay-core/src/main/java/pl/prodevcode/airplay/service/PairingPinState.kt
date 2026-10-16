package pl.prodevcode.airplay.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class PairingPinState {
    private var connections = 0
    private val _prompt = MutableStateFlow<PinPrompt?>(null)
    val prompt = _prompt.asStateFlow()

    @Synchronized
    fun connectionOpened() {
        connections++
    }

    @Synchronized
    fun connectionClosed() {
        connections = (connections - 1).coerceAtLeast(0)
        // iOS closes PAIR-PIN-START before opening the connection that verifies the PIN.
    }

    @Synchronized
    fun show(pin: String, nowMs: Long, validityMs: Long): PinPrompt? {
        if (connections == 0) return null
        return PinPrompt(pin, nowMs, nowMs + validityMs).also { _prompt.value = it }
    }

    @Synchronized
    fun clear() {
        _prompt.value = null
    }

    @Synchronized
    fun reset() {
        connections = 0
        clear()
    }
}
