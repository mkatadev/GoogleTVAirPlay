package pl.prodevcode.airplay.renderer

import pl.prodevcode.airplay.Prefs

/**
 * mirrors native AudioConfig struct; defaults must match preference defaults so a
 * freshly-constructed AudioRenderer is sane
 */
data class AudioConfig(
    val cushionMs: Int = 0,           // 0 = adaptive tuner, otherwise fixed cushion ms
    val percentilePct: Int = 95,
    val oboeBufferFrames: Int = 0,    // 0 = auto (two bursts)
    val forceSwAlac: Boolean = false,
    val realtimePriority: Boolean = true,
    val lowLatency: Boolean = true,
    val benchmarkLog: Boolean = false,
    // Audio-Latency we announce to the sender; audio-only playout runs on the sender's
    // schedule and adds it back on top of the sender's lead
    val declaredLatencyMs: Int = Prefs.DEF_AUDIO_LATENCY_MS,
    // audio-only buffer cap in ms; 0 = play exactly on the sender's schedule
    val scheduledCapMs: Int = 0,
)
