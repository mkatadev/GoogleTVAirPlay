package pl.prodevcode.airplay.service

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.prodevcode.homekit.accessory.InputType
import pl.prodevcode.testing.FakeSharedPreferences

class InputRegistryTest {

    private val prefs = FakeSharedPreferences()
    private val registry = InputRegistry(prefs)
    private val yt = InputRegistry.App("com.google.android.youtube.tv", "YouTube")
    private val nf = InputRegistry.App("com.netflix.ninja", "Netflix")

    @Test fun `home screen and AirPlay come first and apps get ids from 3 that never change`() {
        val first = registry.inputs(listOf(nf, yt))
        assertEquals(listOf(2, 1, 3, 4), first.map { it.id })
        assertEquals(listOf(InputType.HOME_SCREEN, InputType.AIRPLAY, InputType.APPLICATION, InputType.APPLICATION), first.map { it.type })
        assertEquals("Google TV", first[0].name)
        assertEquals(listOf("YouTube", "Netflix"), first.drop(2).map { it.name }) // popularity order, not the order given

        // Netflix uninstalled, a new app installed, Netflix back: everybody keeps their id
        val second = InputRegistry(prefs).inputs(listOf(yt, InputRegistry.App("com.disney.disneyplus", "Disney+")))
        assertEquals(listOf(2, 1, 3, 5), second.map { it.id })
        val third = InputRegistry(prefs).inputs(listOf(nf, yt))
        assertEquals(4, third.first { it.name == "Netflix" }.id)
        assertEquals("com.netflix.ninja", registry.packageFor(4))
        assertEquals(3, registry.idFor(yt.packageName))
        assertNull(registry.packageFor(1))
    }

    @Test fun `names and visibility edited in Home are persisted per id`() {
        registry.inputs(listOf(yt))
        registry.rename(3, "Tube")
        registry.setVisible(3, false)
        registry.setVisible(2, false)
        val inputs = InputRegistry(prefs).inputs(listOf(yt))
        assertEquals("Tube", inputs.first { it.id == 3 }.name)
        assertFalse(inputs.first { it.id == 3 }.visible)
        assertFalse(inputs.first { it.id == 2 }.visible)
        assertTrue(inputs.first { it.id == 1 }.visible)

        registry.rename(3, "  ") // blank restores the app label
        registry.setVisible(2, true)
        val restored = InputRegistry(prefs).inputs(listOf(yt))
        assertEquals("YouTube", restored.first { it.id == 3 }.name)
        assertTrue(restored.first { it.id == 2 }.visible)
    }

    @Test fun `content filter keeps streaming apps and drops tooling`() {
        val own = "pl.prodevcode.tvairplay"
        fun content(pkg: String, category: Int = ApplicationInfo.CATEGORY_UNDEFINED, system: Boolean = false) =
            InputRegistry.isContentApp(pkg, own, category, system)
        assertFalse(content(own))
        assertFalse(content("com.android.tv.settings", system = true))
        assertFalse(content("com.android.vending", system = true))
        assertTrue(content("com.google.android.youtube.tv", system = true))      // preinstalled but known content
        assertTrue(content("pl.tvn.player.tv"))                                   // user-installed TV app
        assertTrue(content("com.example.radio", ApplicationInfo.CATEGORY_AUDIO, system = true))
        assertFalse(content("com.example.tool", ApplicationInfo.CATEGORY_PRODUCTIVITY))
        assertFalse(content("com.example.game", ApplicationInfo.CATEGORY_GAME))
        assertFalse(content("com.vendor.systemthing", system = true))             // unknown preinstalled tool
    }
}
