package pl.prodevcode.tvairplay.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.prodevcode.tvairplay.domain.model.ReceiverSettings

interface SettingsRepository {
    val settings: Flow<ReceiverSettings>
    suspend fun update(transform: (ReceiverSettings) -> ReceiverSettings)
}
