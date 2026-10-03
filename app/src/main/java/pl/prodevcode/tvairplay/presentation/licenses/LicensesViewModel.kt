package pl.prodevcode.tvairplay.presentation.licenses

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import pl.prodevcode.tvairplay.domain.usecase.GetLicenseTextUseCase
import pl.prodevcode.tvairplay.domain.usecase.GetOpenSourceComponentsUseCase
import pl.prodevcode.tvairplay.presentation.mvi.MviViewModel
import pl.prodevcode.tvairplay.presentation.licenses.LicensesIntent as Intent

@HiltViewModel
class LicensesViewModel @Inject constructor(
    getComponents: GetOpenSourceComponentsUseCase,
    private val getLicenseText: GetLicenseTextUseCase,
) : MviViewModel<LicensesUiState, Intent, LicensesEffect>(LicensesUiState(components = getComponents())) {

    override fun onIntent(intent: Intent) {
        when (intent) {
            is Intent.Select -> select(intent)
            Intent.Back ->
                if (currentState.selected != null) setState { copy(selected = null, licenseText = "") }
                else sendEffect(LicensesEffect.NavigateBack)
        }
    }

    private fun select(intent: Intent.Select) {
        setState { copy(selected = intent.component, licenseText = "") }
        viewModelScope.launch {
            val text = runCatching { getLicenseText(intent.component.licenseAsset) }.getOrDefault("")
            if (currentState.selected == intent.component) setState { copy(licenseText = text) }
        }
    }
}
