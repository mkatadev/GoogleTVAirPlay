package pl.prodevcode.tvairplay.presentation.licenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.prodevcode.tvairplay.domain.model.OpenSourceComponent
import pl.prodevcode.tvairplay.domain.usecase.GetLicenseTextUseCase
import pl.prodevcode.tvairplay.domain.usecase.GetOpenSourceComponentsUseCase

data class LicensesUiState(
    val components: List<OpenSourceComponent> = emptyList(),
    val selected: OpenSourceComponent? = null,
    val licenseText: String = "",
)

@HiltViewModel
class LicensesViewModel @Inject constructor(
    getComponents: GetOpenSourceComponentsUseCase,
    private val getLicenseText: GetLicenseTextUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(LicensesUiState(components = getComponents()))
    val state: StateFlow<LicensesUiState> = _state.asStateFlow()

    fun select(component: OpenSourceComponent) {
        _state.value = _state.value.copy(selected = component, licenseText = "")
        viewModelScope.launch {
            val text = runCatching { getLicenseText(component.licenseAsset) }.getOrDefault("")
            _state.value = _state.value.copy(licenseText = text)
        }
    }

    fun closeDetail(): Boolean {
        if (_state.value.selected == null) return false
        _state.value = _state.value.copy(selected = null, licenseText = "")
        return true
    }
}
