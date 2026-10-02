package pl.prodevcode.tvairplay.domain.repository

import kotlinx.coroutines.flow.Flow

/** "Display over other apps" — required on Android TV so the receiver can show itself when media starts in the background. */
interface OverlayPermissionRepository {
    val granted: Flow<Boolean>
    fun refresh()
    fun openSystemSettings()
}
