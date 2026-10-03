package pl.prodevcode.tvairplay.presentation.settings.language

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import pl.prodevcode.tvairplay.domain.model.AppLanguage
import pl.prodevcode.tvairplay.domain.model.ReceiverSettings
import pl.prodevcode.tvairplay.domain.usecase.ObserveSettingsUseCase
import pl.prodevcode.tvairplay.domain.usecase.UpdateSettingsUseCase
import pl.prodevcode.tvairplay.presentation.MainDispatcherRule
import pl.prodevcode.tvairplay.presentation.settings.settingsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class LanguageViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val settings = MutableStateFlow(ReceiverSettings())
    private val repo = settingsRepository(settings)
    private fun viewModel() = LanguageViewModel(ObserveSettingsUseCase(repo), UpdateSettingsUseCase(repo))

    @Test fun `state mirrors the stored language`() = runTest {
        settings.value = ReceiverSettings(language = AppLanguage.POLISH)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(AppLanguage.POLISH, vm.state.value.current)
    }

    @Test fun `pick saves and closes`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(LanguageIntent.Pick(AppLanguage.ENGLISH))
            assertEquals(LanguageEffect.Close, awaitItem())
        }
        assertEquals(AppLanguage.ENGLISH, settings.value.language)
        assertEquals(AppLanguage.ENGLISH, vm.state.value.current)
    }

    @Test fun `back closes without saving`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(LanguageIntent.Back)
            assertEquals(LanguageEffect.Close, awaitItem())
        }
        assertEquals(AppLanguage.SYSTEM, settings.value.language)
    }
}
