package pl.prodevcode.airplay.service

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import pl.prodevcode.airplay.Prefs

enum class LatencyMode(val pref: String) {
    LOW("low"), BALANCED("balanced"), SMOOTH("smooth");

    companion object {
        fun fromPref(value: String?) = entries.firstOrNull { it.pref == value } ?: BALANCED
    }
}

/**
 * One user-facing preset over the individual mirroring/audio latency knobs.
 * `null` means "use the individual preference", which is what BALANCED does so existing
 * installs keep their behaviour.
 */
data class LatencyProfile(
    val allowFrameDrop: Boolean? = null,
    val scheduledOutputRelease: Boolean? = null,
    val audioAdaptiveStep: Int? = null,
    val oboeLowLatency: Boolean? = null,
) {
    companion object {
        fun of(mode: LatencyMode) = when (mode) {
            // render as soon as decoded, let the decoder drop late frames, shallow audio cushion
            LatencyMode.LOW -> LatencyProfile(
                allowFrameDrop = true, scheduledOutputRelease = false, audioAdaptiveStep = 1, oboeLowLatency = true,
            )
            LatencyMode.BALANCED -> LatencyProfile()
            // pace frames to their presentation time and keep every frame; deeper audio cushion
            LatencyMode.SMOOTH -> LatencyProfile(
                allowFrameDrop = false, scheduledOutputRelease = true, audioAdaptiveStep = 4, oboeLowLatency = false,
            )
        }

        fun read(p: SharedPreferences) = of(LatencyMode.fromPref(p.getString(Prefs.LATENCY_MODE, Prefs.DEF_LATENCY_MODE)))
    }
}

fun SharedPreferences.latencyModeFlow(): Flow<LatencyMode> = callbackFlow {
    fun current() = LatencyMode.fromPref(getString(Prefs.LATENCY_MODE, Prefs.DEF_LATENCY_MODE))
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.LATENCY_MODE) trySend(current())
    }
    trySend(current())
    registerOnSharedPreferenceChangeListener(listener)
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}.distinctUntilChanged()
