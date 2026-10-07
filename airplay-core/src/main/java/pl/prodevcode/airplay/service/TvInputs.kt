package pl.prodevcode.airplay.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import pl.prodevcode.homekit.accessory.InputType
import pl.prodevcode.homekit.accessory.TvInput

/**
 * HomeKit inputs of the TV: the receiver itself, the Google TV home screen and the installed
 * content apps. Ids are allocated once per package and persisted so Home automations survive
 * restarts and reinstalls; names and visibility edited in Home are persisted the same way.
 */
internal class TvInputs(private val context: Context, prefs: SharedPreferences) {

    private val registry = InputRegistry(prefs)

    /** Current list: Google TV, AirPlay, then apps by popularity and label. */
    fun inputs(): List<TvInput> = registry.inputs(installedContentApps())

    fun packageFor(id: Int): String? = registry.packageFor(id)
    fun idFor(packageName: String): Int? = registry.idFor(packageName)
    fun rename(id: Int, name: String) = registry.rename(id, name)
    fun setVisible(id: Int, visible: Boolean) = registry.setVisible(id, visible)

    /** Launch intent of the app behind [id], `null` for the fixed inputs or a removed app. */
    fun launchIntent(id: Int): Intent? {
        val pkg = packageFor(id) ?: return null
        return context.packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: context.packageManager.getLaunchIntentForPackage(pkg)
    }

    private fun installedContentApps(): List<InputRegistry.App> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { app ->
                InputRegistry.isContentApp(
                    packageName = app.packageName, ownPackage = context.packageName,
                    category = app.category, system = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            .map { InputRegistry.App(it.packageName, it.loadLabel(pm).toString().ifBlank { it.packageName }) }
    }
}

/** Pure id/name/visibility bookkeeping behind [TvInputs]; JVM-testable. */
internal class InputRegistry(private val prefs: SharedPreferences) {

    data class App(val packageName: String, val label: String)

    fun inputs(unordered: List<App>): List<TvInput> {
        val apps = unordered.sortedWith(compareBy({ PRIORITY.indexOf(it.packageName).let { i -> if (i < 0) PRIORITY.size else i } }, { it.label.lowercase() }))
        val ids = loadIds()
        var changed = false
        apps.forEach { app ->
            if (app.packageName !in ids) {
                ids[app.packageName] = (ids.values.maxOrNull() ?: (FIRST_APP_ID - 1)) + 1
                changed = true
            }
        }
        if (changed) saveIds(ids)
        val names = loadNames()
        val hidden = loadHidden()
        // the home screen is the TV's natural default input; ids stay as they are, only the order matters here
        val fixed = listOf(
            TvInput(ID_HOME, names[ID_HOME] ?: NAME_HOME, InputType.HOME_SCREEN, ID_HOME !in hidden),
            TvInput(ID_AIRPLAY, names[ID_AIRPLAY] ?: NAME_AIRPLAY, InputType.AIRPLAY, ID_AIRPLAY !in hidden),
        )
        val appInputs = apps.mapNotNull { app ->
            val id = ids[app.packageName] ?: return@mapNotNull null
            if (id > TvInput.MAX_ID) return@mapNotNull null
            TvInput(id, names[id] ?: app.label, InputType.APPLICATION, id !in hidden)
        }
        return fixed + appInputs
    }

    fun packageFor(id: Int): String? = loadIds().entries.firstOrNull { it.value == id }?.key
    fun idFor(packageName: String): Int? = loadIds()[packageName]

    fun rename(id: Int, name: String) {
        val names = loadNames()
        if (name.isBlank()) names.remove(id) else names[id] = name.trim()
        prefs.edit { putString(KEY_NAMES, JSONObject(names.mapKeys { it.key.toString() }).toString()) }
    }

    fun setVisible(id: Int, visible: Boolean) {
        val hidden = loadHidden()
        if (visible) hidden.remove(id) else hidden.add(id)
        prefs.edit { putString(KEY_HIDDEN, JSONArray(hidden.sorted()).toString()) }
    }

    private fun loadIds(): LinkedHashMap<String, Int> {
        val out = LinkedHashMap<String, Int>()
        val json = prefs.getString(KEY_IDS, null) ?: return out
        runCatching {
            val o = JSONObject(json)
            o.keys().forEach { k -> out[k] = o.getInt(k) }
        }
        return out
    }

    private fun saveIds(ids: Map<String, Int>) = prefs.edit { putString(KEY_IDS, JSONObject(ids).toString()) }

    private fun loadNames(): HashMap<Int, String> {
        val out = HashMap<Int, String>()
        val json = prefs.getString(KEY_NAMES, null) ?: return out
        runCatching {
            val o = JSONObject(json)
            o.keys().forEach { k -> k.toIntOrNull()?.let { out[it] = o.getString(k) } }
        }
        return out
    }

    private fun loadHidden(): HashSet<Int> {
        val out = HashSet<Int>()
        val json = prefs.getString(KEY_HIDDEN, null) ?: return out
        runCatching { val a = JSONArray(json); for (i in 0 until a.length()) out += a.getInt(i) }
        return out
    }

    companion object {
        const val ID_AIRPLAY = 1
        const val ID_HOME = 2
        const val FIRST_APP_ID = 3
        const val NAME_AIRPLAY = "AirPlay"
        const val NAME_HOME = "Google TV"

        private const val KEY_IDS = "input_ids"
        private const val KEY_NAMES = "input_names"
        private const val KEY_HIDDEN = "input_hidden"

        /** Most-used streaming apps first; everything else follows alphabetically. */
        private val PRIORITY = listOf(
            "com.google.android.youtube.tv", "com.netflix.ninja", "com.disney.disneyplus", "com.wbd.stream", "com.hbo.hbonow",
            "com.amazon.amazonvideo.livingroom", "com.apple.atve.androidtv.appletv", "com.spotify.tv.android",
            "com.google.android.youtube.tvmusic", "com.google.android.videos", "com.canal.android.canal", "pl.tvn.player.tv",
            "pl.cyfrowypolsat.cpgo", "com.tvp.vodtv.tv", "tv.twitch.android.app", "com.plexapp.android",
        )

        /** Launcher, store and system tooling that are not "content" even though they sit on the TV home screen. */
        private val EXCLUDED = setOf(
            "com.android.tv.settings", "com.android.vending", "com.google.android.play.games",
            "com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher", "com.google.android.tvrecommendations",
            "com.google.android.katniss", "com.google.android.apps.mediashell", "com.google.android.tv.remote.service",
            "com.google.android.gms", "com.google.android.backdrop", "com.google.android.tv.frameworkpackagestubs",
        )

        /** Preinstalled apps that are content even though they carry the system flag. */
        private val KNOWN_CONTENT = setOf(
            "com.google.android.youtube.tv", "com.google.android.youtube.tvmusic", "com.google.android.youtube.tvkids",
            "com.google.android.videos", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus",
            "com.spotify.tv.android", "com.apple.atve.androidtv.appletv", "com.wbd.stream", "com.hbo.hbonow",
            "tv.twitch.android.app", "com.plexapp.android", "org.jellyfin.androidtv", "org.videolan.vlc", "org.xbmc.kodi",
        )

        /**
         * Content = user-installed TV apps and known streaming apps; tools, games and the launcher are out.
         * [category] is [ApplicationInfo.category] (−1 when the app does not declare one).
         */
        fun isContentApp(packageName: String, ownPackage: String, category: Int, system: Boolean): Boolean {
            if (packageName == ownPackage || packageName in EXCLUDED) return false
            if (packageName in KNOWN_CONTENT) return true
            return when (category) {
                ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_AUDIO -> true
                ApplicationInfo.CATEGORY_GAME, ApplicationInfo.CATEGORY_PRODUCTIVITY,
                ApplicationInfo.CATEGORY_MAPS, ApplicationInfo.CATEGORY_ACCESSIBILITY -> false
                else -> !system
            }
        }
    }
}
