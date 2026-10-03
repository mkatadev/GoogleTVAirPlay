package pl.prodevcode.tvairplay.presentation.settings

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import pl.prodevcode.tvairplay.domain.model.ReceiverSettings
import pl.prodevcode.tvairplay.domain.repository.SettingsRepository

/** In-memory [SettingsRepository] mock: `update` applies the transform to [flow]. */
fun settingsRepository(flow: MutableStateFlow<ReceiverSettings>): SettingsRepository = mockk {
    every { settings } returns flow
    coEvery { update(any()) } answers {
        flow.value = firstArg<(ReceiverSettings) -> ReceiverSettings>()(flow.value)
    }
}
