package pl.prodevcode.airplay.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinUiPolicyTest {
    private val prompt = PinPrompt("1234", shownAtMs = 1_000L, expiresAtMs = 61_000L)

    @Test
    fun `current PIN can foreground the receiver before media starts`() {
        assertTrue(shouldLaunch())
    }

    @Test
    fun `disabled launch on connect does not foreground the PIN`() {
        assertFalse(shouldLaunch(launchOnConnect = false))
    }

    @Test
    fun `user dismissed UI is not reopened by a PIN`() {
        assertFalse(shouldLaunch(uiDismissed = true))
    }

    @Test
    fun `cleared PIN cannot foreground the UI from a queued callback`() {
        assertFalse(shouldLaunch(current = null))
    }

    @Test
    fun `replaced PIN cannot foreground the UI from an old callback`() {
        assertFalse(shouldLaunch(current = prompt.copy(pin = "5678")))
    }

    @Test
    fun `reissued identical digits are a different PIN request`() {
        assertFalse(shouldLaunch(current = prompt.copy(shownAtMs = 2_000L, expiresAtMs = 62_000L)))
    }

    @Test
    fun `PIN is valid until but not at expiry`() {
        assertTrue(shouldLaunch(nowMs = 60_999L))
        assertFalse(shouldLaunch(nowMs = 61_000L))
        assertFalse(shouldLaunch(nowMs = 61_001L))
    }

    private fun shouldLaunch(
        current: PinPrompt? = prompt,
        nowMs: Long = 1_000L,
        launchOnConnect: Boolean = true,
        uiDismissed: Boolean = false,
    ) = shouldBringPinUiToFront(prompt, current, nowMs, launchOnConnect, uiDismissed)
}
