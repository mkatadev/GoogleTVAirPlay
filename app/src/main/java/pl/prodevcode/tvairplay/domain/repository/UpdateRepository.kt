package pl.prodevcode.tvairplay.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.prodevcode.tvairplay.domain.model.UpdateCheck

interface UpdateRepository {
    val state: Flow<UpdateCheck>
    /** Queries the release feed; cached for a while unless [force]. */
    suspend fun check(force: Boolean = false)
}
