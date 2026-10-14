package pl.prodevcode.airplay.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.prodevcode.airplay.Prefs
import pl.prodevcode.testing.FakeSharedPreferences

class LatencyProfileTest {

    private val prefs = FakeSharedPreferences()

    @Test fun `balanced leaves every knob to the individual preferences`() {
        val p = LatencyProfile.of(LatencyMode.BALANCED)
        assertNull(p.allowFrameDrop); assertNull(p.scheduledOutputRelease)
        assertNull(p.audioAdaptiveStep); assertNull(p.oboeLowLatency)
    }

    @Test fun `low trades smoothness for delay, smooth the other way`() {
        val low = LatencyProfile.of(LatencyMode.LOW)
        assertEquals(true, low.allowFrameDrop); assertEquals(false, low.scheduledOutputRelease); assertEquals(true, low.oboeLowLatency)
        val smooth = LatencyProfile.of(LatencyMode.SMOOTH)
        assertEquals(false, smooth.allowFrameDrop); assertEquals(true, smooth.scheduledOutputRelease); assertEquals(false, smooth.oboeLowLatency)
        assertTrue(low.audioAdaptiveStep!! < smooth.audioAdaptiveStep!!)
    }

    @Test fun `unknown or missing preference falls back to balanced`() {
        assertEquals(LatencyMode.BALANCED, LatencyMode.fromPref(null))
        assertEquals(LatencyMode.BALANCED, LatencyMode.fromPref("turbo"))
        assertEquals(LatencyProfile(), LatencyProfile.read(prefs))
    }

    @Test fun `audio config follows the profile`() {
        prefs.edit().putString(Prefs.LATENCY_MODE, LatencyMode.LOW.pref).apply()
        val low = readAudioConfig(prefs)
        assertTrue(low.lowLatency)
        assertEquals(Prefs.ADAPTIVE_PERCENTILES[1], low.percentilePct)

        prefs.edit().putString(Prefs.LATENCY_MODE, LatencyMode.SMOOTH.pref).apply()
        val smooth = readAudioConfig(prefs)
        assertFalse(smooth.lowLatency)
        assertEquals(Prefs.ADAPTIVE_PERCENTILES[4], smooth.percentilePct)
    }

    @Test fun `declared latency mirrors what the sender is told`() {
        assertEquals(Prefs.DEF_AUDIO_LATENCY_MS, readAudioConfig(prefs).declaredLatencyMs)
        prefs.edit().putInt(Prefs.AUDIO_LATENCY_MS, 400).apply()
        assertEquals(400, readAudioConfig(prefs).declaredLatencyMs)
        prefs.edit().putInt(Prefs.AUDIO_LATENCY_MS, -1).apply()
        assertEquals(0, readAudioConfig(prefs).declaredLatencyMs)
    }

    @Test fun `music delay caps the scheduled buffer, airplay default leaves it uncapped`() {
        assertEquals(MusicDelay.AIRPLAY, MusicDelay.fromPref(null))
        assertEquals(MusicDelay.AIRPLAY, MusicDelay.fromPref("huge"))
        assertEquals(0, readAudioConfig(prefs).scheduledCapMs)
        prefs.edit().putString(Prefs.MUSIC_DELAY, MusicDelay.SHORT.pref).apply()
        assertEquals(500, readAudioConfig(prefs).scheduledCapMs)
        prefs.edit().putString(Prefs.MUSIC_DELAY, MusicDelay.MEDIUM.pref).apply()
        assertEquals(1000, readAudioConfig(prefs).scheduledCapMs)
    }
}
