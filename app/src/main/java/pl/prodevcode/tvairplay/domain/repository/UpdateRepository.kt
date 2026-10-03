package pl.prodevcode.tvairplay.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.prodevcode.tvairplay.domain.model.InstallProgress
import pl.prodevcode.tvairplay.domain.model.UpdateCheck

interface UpdateRepository {
    val state: Flow<UpdateCheck>
    val install: Flow<InstallProgress>
    /** Queries the release feed; cached for a while unless [force]. */
    suspend fun check(force: Boolean = false)
    /** Downloads the available APK, verifies it and hands it to the system installer. */
    suspend fun downloadAndInstall()
}
