package pl.prodevcode.tvairplay.data.permission

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pl.prodevcode.tvairplay.domain.repository.OverlayPermissionRepository

@Singleton
class OverlayPermissionRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : OverlayPermissionRepository {

    private val _granted = MutableStateFlow(Settings.canDrawOverlays(context))
    override val granted: StateFlow<Boolean> = _granted

    override fun refresh() { _granted.value = Settings.canDrawOverlays(context) }

    override fun openSystemSettings() {
        val perApp = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
        val generic = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        // some TV builds only ship the list screen, others only the per-app one
        for (intent in listOf(perApp, generic)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // no resolveActivity(): it needs a <queries> entry; ActivityNotFoundException tells us the same
            runCatching { context.startActivity(intent) }
                .onSuccess { return }
                .onFailure { Log.w(TAG, "overlay settings failed", it) }
        }
        // last resort: the app's own details page (has "Display over other apps" under Advanced on TV)
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private companion object { const val TAG = "OverlayPermission" }
}
