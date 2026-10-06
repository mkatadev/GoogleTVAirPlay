package pl.prodevcode.tvairplay.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
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
import pl.prodevcode.tvairplay.domain.model.InstallFailure
import pl.prodevcode.tvairplay.domain.model.InstallProgress
import pl.prodevcode.tvairplay.domain.model.UpdateCheck
import pl.prodevcode.tvairplay.domain.model.VersionComparator
import pl.prodevcode.tvairplay.domain.repository.UpdateRepository

/**
 * Reads the latest GitHub release and, on request, downloads its APK, checks the published
 * SHA-256 and hands it to [PackageInstaller]. The system still asks the user to confirm;
 * that is as far as a non-system app can go.
 *
 * The version comes from the `releases/latest` redirect on github.com, which has no rate limit;
 * the REST API (60 unauthenticated requests per hour per IP, shared by every device behind the
 * same router) is only consulted for asset metadata and may fail without breaking the check.
 */
@Singleton
class GitHubUpdateRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : UpdateRepository {

    private val _state = MutableStateFlow<UpdateCheck>(UpdateCheck.Idle)
    override val state: StateFlow<UpdateCheck> = _state

    private val _install = MutableStateFlow<InstallProgress>(InstallProgress.Idle)
    override val install: StateFlow<InstallProgress> = _install

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
                .map { update ->
                    if (VersionComparator.compare(update.latestVersion, currentVersion) > 0) UpdateCheck.Available(update)
                    else UpdateCheck.UpToDate(currentVersion)
                }
                .getOrElse { UpdateCheck.Failed(currentVersion) }
        }
        lastCheckedAt = now
    }

    override suspend fun downloadAndInstall() {
        val update = (_state.value as? UpdateCheck.Available)?.update ?: return
        val apkUrl = update.apkUrl ?: return
        if (_install.value is InstallProgress.Downloading || _install.value == InstallProgress.Verifying) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            _install.value = InstallProgress.NeedsPermission
            openUnknownSourcesSettings()
            return
        }
        withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "updates/AirPlay-for-Google-TV-v${update.latestVersion}.apk")
            try {
                file.parentFile?.mkdirs()
                _install.value = InstallProgress.Downloading(0)
                download(apkUrl, file, update.apkSizeBytes) { _install.value = InstallProgress.Downloading(it) }
            } catch (e: Exception) {
                Log.w(TAG, "download failed", e)
                file.delete()
                _install.value = InstallProgress.Failed(InstallFailure.DOWNLOAD)
                return@withContext
            }
            update.apkSha256Url?.let { shaUrl ->
                _install.value = InstallProgress.Verifying
                val expected = runCatching { URL(shaUrl).readText().trim().split(Regex("\\s+")).first().lowercase() }.getOrNull()
                if (expected == null || expected != sha256(file)) {
                    Log.w(TAG, "checksum mismatch for ${file.name}")
                    file.delete()
                    _install.value = InstallProgress.Failed(InstallFailure.CHECKSUM)
                    return@withContext
                }
            }
            try {
                commitToInstaller(file)
                _install.value = InstallProgress.AwaitingConfirmation
            } catch (e: Exception) {
                Log.w(TAG, "installer session failed", e)
                _install.value = InstallProgress.Failed(InstallFailure.INSTALLER)
            } finally {
                file.delete()
            }
        }
    }

    /** Called by [UpdateInstallReceiver] with the installer's status broadcast. */
    fun onInstallerStatus(status: Int, message: String?, confirmIntent: Intent?) {
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                confirmIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(it) }.onFailure { e -> Log.w(TAG, "confirm failed", e) }
                }
                _install.value = InstallProgress.AwaitingConfirmation
            }
            // success means the process is about to be replaced; nothing to show
            PackageInstaller.STATUS_SUCCESS -> _install.value = InstallProgress.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> _install.value = InstallProgress.Failed(InstallFailure.ABORTED)
            else -> {
                Log.w(TAG, "install failed: $status $message")
                _install.value = InstallProgress.Failed(InstallFailure.INSTALLER)
            }
        }
    }

    private fun fetchLatest(): AppUpdate {
        val (tag, releaseUrl) = latestTag()
        // release.yml publishes assets under fixed names, so no API call is needed to find them
        val apkName = "AirPlay-for-Google-TV-v$tag.apk"
        val base = "$REPO_URL/releases/download/v$tag/"
        val size = runCatching { apkSizeFromApi(tag, apkName) }
            .onFailure { Log.d(TAG, "asset metadata unavailable: ${it.message}") }
            .getOrDefault(0L)
        return AppUpdate(
            currentVersion = currentVersion, latestVersion = tag, releaseUrl = releaseUrl,
            apkUrl = base + apkName, apkSha256Url = "$base$apkName.sha256", apkSizeBytes = size,
        )
    }

    /** Follows `releases/latest` to `releases/tag/vX.Y.Z` without touching the rate-limited API. */
    private fun latestTag(): Pair<String, String> {
        val conn = open(RELEASES_PAGE, follow = false)
        try {
            val location = conn.getHeaderField("Location")
            if (conn.responseCode !in 300..399 || location == null) error("HTTP ${conn.responseCode}")
            val tag = location.substringAfter("/releases/tag/", "").removePrefix("v")
            if (!VersionComparator.isRelease(tag)) error("unexpected redirect $location")
            return tag to location
        } finally {
            conn.disconnect()
        }
    }

    private fun apkSizeFromApi(tag: String, apkName: String): Long {
        val json = JSONObject(get("$API_URL/releases/tags/v$tag", accept = "application/vnd.github+json"))
        val assets = json.optJSONArray("assets") ?: return 0L
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name") == apkName) return a.optLong("size")
        }
        return 0L
    }

    private fun open(url: String, accept: String? = null, follow: Boolean = true): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            accept?.let { setRequestProperty("Accept", it) }
            setRequestProperty("User-Agent", "GoogleTVAirPlay/$currentVersion")
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = follow
        }

    private fun get(url: String, accept: String? = null): String {
        val conn = open(url, accept)
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun download(url: String, target: File, knownSize: Long, onProgress: (Int) -> Unit) {
        val conn = open(url)
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val total = if (knownSize > 0) knownSize else conn.contentLengthLong
            var done = 0L; var lastPercent = -1
            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPercent) { lastPercent = pct; onProgress(pct) }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun commitToInstaller(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("update.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, UpdateInstallReceiver::class.java).setAction(UpdateInstallReceiver.ACTION)
            val pi = PendingIntent.getBroadcast(
                context, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pi.intentSender)
        }
    }

    private fun openUnknownSourcesSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure { Log.w(TAG, "unknown sources screen unavailable", it) }
    }

    private companion object {
        const val TAG = "UpdateCheck"
        const val REPO_URL = "https://github.com/mkatadev/GoogleTVAirPlay"
        const val API_URL = "https://api.github.com/repos/mkatadev/GoogleTVAirPlay"
        const val RELEASES_PAGE = "$REPO_URL/releases/latest"
        const val TIMEOUT_MS = 15_000
        const val CACHE_MS = 6 * 60 * 60 * 1000L
    }
}
