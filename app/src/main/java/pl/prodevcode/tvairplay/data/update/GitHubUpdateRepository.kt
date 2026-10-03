package pl.prodevcode.tvairplay.data.update

import android.content.Context
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pl.prodevcode.tvairplay.domain.model.AppUpdate
import pl.prodevcode.tvairplay.domain.model.UpdateCheck
import pl.prodevcode.tvairplay.domain.model.VersionComparator
import pl.prodevcode.tvairplay.domain.repository.UpdateRepository

/** Reads the latest GitHub release; no downloads — install.sh / Releases do that. */
@Singleton
class GitHubUpdateRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : UpdateRepository {

    private val _state = MutableStateFlow<UpdateCheck>(UpdateCheck.Idle)
    override val state: StateFlow<UpdateCheck> = _state

    private val mutex = Mutex()
    private var lastCheckedAt = 0L

    private val currentVersion: String by lazy {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "dev"
    }

    override suspend fun check(force: Boolean): Unit = mutex.withLock {
        val now = SystemClock.elapsedRealtime()
        if (!force && lastCheckedAt != 0L && now - lastCheckedAt < CACHE_MS) return
        if (!VersionComparator.isRelease(currentVersion)) {
            _state.value = UpdateCheck.UpToDate(currentVersion)
            return
        }
        _state.value = UpdateCheck.Checking
        _state.value = withContext(Dispatchers.IO) {
            runCatching { fetchLatest() }
                .onFailure { Log.w(TAG, "update check failed", it) }
                .map { (latest, url) ->
                    if (VersionComparator.compare(latest, currentVersion) > 0)
                        UpdateCheck.Available(AppUpdate(currentVersion, latest, url))
                    else UpdateCheck.UpToDate(currentVersion)
                }
                .getOrElse { UpdateCheck.Failed(currentVersion) }
        }
        lastCheckedAt = now
    }

    private fun fetchLatest(): Pair<String, String> {
        val conn = (URL(LATEST_RELEASE).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "GoogleTVAirPlay/$currentVersion")
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.getString("tag_name").removePrefix("v")
            return tag to json.optString("html_url", RELEASES_PAGE)
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val TAG = "UpdateCheck"
        const val LATEST_RELEASE = "https://api.github.com/repos/mkatadev/GoogleTVAirPlay/releases/latest"
        const val RELEASES_PAGE = "https://github.com/mkatadev/GoogleTVAirPlay/releases/latest"
        const val TIMEOUT_MS = 8_000
        const val CACHE_MS = 6 * 60 * 60 * 1000L
    }
}
