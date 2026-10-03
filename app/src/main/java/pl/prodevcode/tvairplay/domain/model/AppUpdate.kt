package pl.prodevcode.tvairplay.domain.model

data class AppUpdate(
    val currentVersion: String,
    val latestVersion: String,
    val releaseUrl: String,
    /** Direct APK asset of the release when it has one; enables in-app install. */
    val apkUrl: String? = null,
    val apkSha256Url: String? = null,
    val apkSizeBytes: Long = 0,
) {
    val installable: Boolean get() = apkUrl != null
}

/** Progress of the in-app update: download → verify → system install prompt. */
sealed interface InstallProgress {
    data object Idle : InstallProgress
    data class Downloading(val percent: Int) : InstallProgress
    data object Verifying : InstallProgress
    /** The system "install this app?" dialog is on screen; the user confirms with the remote. */
    data object AwaitingConfirmation : InstallProgress
    /** "Install unknown apps" has to be allowed for this app first; the system screen was opened. */
    data object NeedsPermission : InstallProgress
    data class Failed(val reason: InstallFailure) : InstallProgress
}

enum class InstallFailure { DOWNLOAD, CHECKSUM, INSTALLER, ABORTED }

sealed interface UpdateCheck {
    data object Idle : UpdateCheck
    data object Checking : UpdateCheck
    data class UpToDate(val currentVersion: String) : UpdateCheck
    data class Available(val update: AppUpdate) : UpdateCheck
    data class Failed(val currentVersion: String) : UpdateCheck
}

/** Compares dotted release versions ("1.10.2" > "1.9.0"); non-numeric parts compare as 0. */
object VersionComparator : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        val pa = parts(a); val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = (pa.getOrNull(i) ?: 0).compareTo(pb.getOrNull(i) ?: 0)
            if (d != 0) return d
        }
        return 0
    }

    private fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-').substringBefore('+')
        .split('.').map { it.toIntOrNull() ?: 0 }

    /** Dev builds ("dev", "") are never offered an update. */
    fun isRelease(v: String) = v.firstOrNull()?.isDigit() == true
}
