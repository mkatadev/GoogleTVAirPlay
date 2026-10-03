package pl.prodevcode.tvairplay.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Receives PackageInstaller status for the in-app update session. */
@AndroidEntryPoint
class UpdateInstallReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: GitHubUpdateRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") // typed overload only exists from API 33
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
        repository.onInstallerStatus(status, message, confirm)
    }

    companion object {
        const val ACTION = "pl.prodevcode.tvairplay.UPDATE_INSTALL_STATUS"
    }
}
