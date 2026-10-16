package pl.prodevcode.airplay.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioFocusControllerTest {
    private class FakeFocusAccess : AudioFocusAccess {
        var granted = true
        val listeners = mutableListOf<() -> Unit>()
        var abandoned = 0

        override fun request(onLoss: () -> Unit): Boolean {
            listeners += onLoss
            return granted
        }

        override fun abandon() {
            abandoned++
        }
    }

    private val access = FakeFocusAccess()
    private val interruptions = mutableListOf<String>()
    private val controller = AudioFocusController(access, interruptions::add)

    @Test fun `active audio requests focus only once`() {
        controller.update(true)
        controller.update(true)
        assertEquals(1, access.listeners.size)
        assertEquals(emptyList<String>(), interruptions)
    }

    @Test fun `idle audio never requests focus`() {
        controller.update(false)
        assertEquals(0, access.listeners.size)
        assertEquals(0, access.abandoned)
    }

    @Test fun `focus loss releases focus and disconnects once`() {
        controller.update(true)
        access.listeners.single().invoke()
        access.listeners.single().invoke()
        assertEquals(listOf("Audio focus lost"), interruptions)
        assertEquals(1, access.abandoned)
    }

    @Test fun `denied focus disconnects instead of playing over another app`() {
        access.granted = false
        controller.update(true)
        assertEquals(listOf("Audio focus request denied"), interruptions)
        assertEquals(1, access.abandoned)
    }

    @Test fun `pause or session end releases focus without disconnecting`() {
        controller.update(true)
        controller.update(false)
        controller.release()
        access.listeners.single().invoke()
        assertEquals(1, access.abandoned)
        assertEquals(emptyList<String>(), interruptions)
    }

    @Test fun `resuming audio requests focus again`() {
        controller.update(true)
        controller.update(false)
        controller.update(true)
        assertEquals(2, access.listeners.size)
    }

    @Test fun `late callback from previous request cannot disconnect a new session`() {
        controller.update(true)
        controller.release()
        controller.update(true)
        access.listeners.first().invoke()
        assertEquals(emptyList<String>(), interruptions)
        access.listeners.last().invoke()
        assertEquals(listOf("Audio focus lost"), interruptions)
    }
}
