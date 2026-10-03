package pl.prodevcode.tvairplay.presentation.licenses

import pl.prodevcode.tvairplay.domain.model.OpenSourceComponent
import pl.prodevcode.tvairplay.presentation.mvi.UiEffect
import pl.prodevcode.tvairplay.presentation.mvi.UiIntent
import pl.prodevcode.tvairplay.presentation.mvi.UiState

data class LicensesUiState(
    val components: List<OpenSourceComponent> = emptyList(),
    val selected: OpenSourceComponent? = null,
    val licenseText: String = "",
) : UiState

sealed interface LicensesIntent : UiIntent {
    data class Select(val component: OpenSourceComponent) : LicensesIntent
    /** Back: closes the detail pane if one is open, otherwise leaves the screen. */
    data object Back : LicensesIntent
}

sealed interface LicensesEffect : UiEffect {
    data object NavigateBack : LicensesEffect
}
