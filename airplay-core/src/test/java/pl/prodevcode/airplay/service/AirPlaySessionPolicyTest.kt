package pl.prodevcode.airplay.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AirPlaySessionPolicyTest {

    private val receiverPackage = "pl.prodevcode.tvairplay"

    @Test
    fun `switching to another app disconnects an active audio session`() {
        assertTrue(
            shouldDisconnectAirPlayAudio(
                foregroundPackage = "com.google.android.youtube.tv",
                receiverPackage = receiverPackage,
                audioOnly = true,
                connectionCount = 1,
            ),
        )
    }

    @Test
    fun `staying in receiver does not disconnect audio`() {
        assertFalse(
            shouldDisconnectAirPlayAudio(
                foregroundPackage = receiverPackage,
                receiverPackage = receiverPackage,
                audioOnly = true,
                connectionCount = 1,
            ),
        )
    }

    @Test
    fun `switching apps does not disconnect video or mirroring`() {
        assertFalse(
            shouldDisconnectAirPlayAudio(
                foregroundPackage = "com.google.android.youtube.tv",
                receiverPackage = receiverPackage,
                audioOnly = false,
                connectionCount = 1,
            ),
        )
    }

    @Test
    fun `switching apps does not disconnect when there is no active connection`() {
        assertFalse(
            shouldDisconnectAirPlayAudio(
                foregroundPackage = "com.google.android.youtube.tv",
                receiverPackage = receiverPackage,
                audioOnly = true,
                connectionCount = 0,
            ),
        )
    }

    @Test
    fun `unknown foreground package does not disconnect audio`() {
        assertFalse(
            shouldDisconnectAirPlayAudio(
                foregroundPackage = null,
                receiverPackage = receiverPackage,
                audioOnly = true,
                connectionCount = 1,
            ),
        )
    }
}
