package pl.prodevcode.airplay.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.prodevcode.airplay.Prefs
import pl.prodevcode.airplay.testing.FakeSharedPreferences

class TrustedDeviceStoreTest {

    private val prefs = FakeSharedPreferences()
    private var now = 1_000L
    private fun store() = TrustedDeviceStore(prefs) { now }

    @Test fun `a sender that entered the pin is trusted afterwards and survives a restart`() {
        val s = store()
        assertFalse(s.isTrusted("pk-a"))
        s.register("AA:BB", "pk-a", "Ania's iPhone")
        assertTrue(s.isTrusted("pk-a"))

        val reloaded = store()
        assertTrue(reloaded.isTrusted("pk-a"))
        assertEquals("Ania's iPhone", reloaded.devices.value.single().name)
    }

    @Test fun `trusting updates last seen, re-registering updates the name`() {
        val s = store()
        s.register("id", "pk", "Old name")
        now = 5_000
        assertTrue(s.isTrusted("pk"))
        assertEquals(5_000, s.devices.value.single().lastSeenAt)
        assertEquals(1_000, s.devices.value.single().addedAt)

        s.register("id", "pk", "New name")
        assertEquals(1, s.devices.value.size)
        assertEquals("New name", s.devices.value.single().name)
    }

    @Test fun `forget and forget all`() {
        val s = store()
        s.register("1", "pk-1", "One"); s.register("2", "pk-2", "Two")
        s.forget("pk-1")
        assertFalse(s.isTrusted("pk-1")); assertTrue(s.isTrusted("pk-2"))
        s.forgetAll()
        assertTrue(s.devices.value.isEmpty())
        assertFalse(s.isTrusted("pk-2"))
    }

    @Test fun `with remembering off, pairing lasts only for the current run`() {
        prefs.edit().putBoolean(Prefs.REMEMBER_DEVICES, false).apply()
        val s = store()
        s.register("id", "pk", "Phone")
        assertTrue("same run skips the pin", s.isTrusted("pk"))
        assertTrue(s.devices.value.isEmpty())
        assertFalse("next run asks again", store().isTrusted("pk"))
    }

    @Test fun `previously remembered devices are ignored while remembering is off`() {
        store().register("id", "pk", "Phone")
        prefs.edit().putBoolean(Prefs.REMEMBER_DEVICES, false).apply()
        assertFalse(store().isTrusted("pk"))
        prefs.edit().putBoolean(Prefs.REMEMBER_DEVICES, true).apply()
        assertTrue(store().isTrusted("pk"))
    }

    @Test fun `corrupt storage degrades to an empty list`() {
        prefs.edit().putString(Prefs.TRUSTED_DEVICES, "not json").apply()
        assertTrue(store().devices.value.isEmpty())
    }
}
