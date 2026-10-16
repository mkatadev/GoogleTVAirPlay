package pl.prodevcode.airplay.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingPinStateTest {
    private val state = PairingPinState()

    @Test fun `PIN survives PIN start connection closing before verification`() {
        state.connectionOpened()
        val prompt = state.show("1234", 1_000, 60_000)
        state.connectionClosed()
        assertEquals(prompt, state.prompt.value)
        state.connectionOpened()
        assertEquals(prompt, state.prompt.value)
    }

    @Test fun `closing auxiliary connection does not clear a live pairing PIN`() {
        state.connectionOpened()
        state.connectionOpened()
        val prompt = state.show("1234", 1_000, 60_000)
        state.connectionClosed()
        assertEquals(prompt, state.prompt.value)
        state.connectionClosed()
        assertEquals(prompt, state.prompt.value)
    }

    @Test fun `late display callback cannot restore PIN after disconnect`() {
        state.connectionOpened()
        state.connectionClosed()
        assertNull(state.show("1234", 2_000, 60_000))
        assertNull(state.prompt.value)
    }

    @Test fun `retry after failed pairing replaces old PIN and expiry`() {
        state.connectionOpened()
        state.show("1234", 1_000, 60_000)
        state.connectionClosed()
        state.connectionOpened()
        state.show("5678", 2_000, 60_000)
        assertEquals(PinPrompt("5678", 2_000, 62_000), state.prompt.value)
    }

    @Test fun `retry with identical digits refreshes prompt instead of retaining old expiry`() {
        state.connectionOpened()
        state.show("1234", 1_000, 60_000)
        state.show("1234", 2_000, 60_000)
        assertEquals(PinPrompt("1234", 2_000, 62_000), state.prompt.value)
    }

    @Test fun `receiver shutdown invalidates prompt and late callbacks`() {
        state.connectionOpened()
        state.show("1234", 1_000, 60_000)
        state.reset()
        assertNull(state.prompt.value)
        assertNull(state.show("5678", 2_000, 60_000))
    }

    @Test fun `successful pairing clears prompt without invalidating live connection`() {
        state.connectionOpened()
        state.show("1234", 1_000, 60_000)
        state.clear()
        assertNull(state.prompt.value)
        assertEquals(PinPrompt("5678", 2_000, 62_000), state.show("5678", 2_000, 60_000))
    }
}
