package pl.prodevcode.airplay.audio

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingStateTest {
    private var time = 1_000L
    private val state = NowPlayingState { time }

    private fun metadata(title: String): ByteArray {
        val text = title.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(8 + text.size)
            .put("minm".toByteArray(Charsets.US_ASCII))
            .putInt(text.size)
            .put(text)
            .array()
    }

    @Test fun `new receiver rejects callbacks without a session`() {
        assertNull(state.onMetadata(metadata("Old song")))
        assertFalse(state.onCoverArt(byteArrayOf(1)))
        assertFalse(state.onProgress(0, 44_100, 441_000))
        state.setPlaying(true)
        assertIdle()
    }

    @Test fun `ending session clears track progress and playback`() {
        state.beginSession()
        assertEquals("Song", state.onMetadata(metadata("Song"))?.title)
        assertTrue(state.onProgress(0, 44_100, 441_000))
        assertTrue(state.playing.value)
        state.endSession()
        assertIdle()
    }

    @Test fun `late metadata artwork progress and play cannot revive ended session`() {
        state.beginSession()
        state.onMetadata(metadata("Song"))
        state.onProgress(0, 44_100, 441_000)
        state.endSession()
        assertNull(state.onMetadata(metadata("Song")))
        assertFalse(state.onCoverArt(byteArrayOf(1)))
        assertFalse(state.onProgress(0, 88_200, 441_000))
        state.setPlaying(true)
        time += 5_000
        assertIdle()
        assertEquals(0L, state.currentPositionMs())
    }

    @Test fun `new connection accepts a new song after disconnect`() {
        state.beginSession()
        state.onMetadata(metadata("Old song"))
        state.endSession()
        state.beginSession()
        assertEquals("New song", state.onMetadata(metadata("New song"))?.title)
        assertTrue(state.onProgress(0, 44_100, 441_000))
        assertTrue(state.playing.value)
        assertEquals(1_000L, state.positionMs.value)
        assertEquals(10_000L, state.durationMs.value)
    }

    @Test fun `clearing metadata within a session does not close the session`() {
        state.beginSession()
        state.onMetadata(metadata("Song"))
        state.clear()
        assertTrue(state.sessionActive)
        assertEquals("Next song", state.onMetadata(metadata("Next song"))?.title)
    }

    private fun assertIdle() {
        assertFalse(state.sessionActive)
        assertEquals(TrackInfo(), state.track.value)
        assertNull(state.coverArtBytes)
        assertEquals(0L, state.positionMs.value)
        assertEquals(0L, state.durationMs.value)
        assertFalse(state.playing.value)
    }
}
