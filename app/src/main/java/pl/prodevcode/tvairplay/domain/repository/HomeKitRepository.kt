package pl.prodevcode.tvairplay.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.prodevcode.tvairplay.domain.model.HomeKitStatus

interface HomeKitRepository {
    val status: Flow<HomeKitStatus>
    /** Forget every paired controller; the TV must be added to Home again. */
    fun resetPairings()
}
