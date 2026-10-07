package pl.prodevcode.tvairplay.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.prodevcode.tvairplay.presentation.diagnostics.DiagnosticsScreen
import pl.prodevcode.tvairplay.presentation.licenses.LicensesScreen
import pl.prodevcode.tvairplay.domain.model.SessionMode
import pl.prodevcode.tvairplay.presentation.receiver.ReceiverIntent
import pl.prodevcode.tvairplay.presentation.receiver.ReceiverScreen
import pl.prodevcode.tvairplay.presentation.receiver.ReceiverViewModel
import pl.prodevcode.tvairplay.presentation.receiver.screens.PinOverlay
import pl.prodevcode.tvairplay.presentation.settings.SettingsScreen
import pl.prodevcode.tvairplay.presentation.settings.SettingsSub
import pl.prodevcode.tvairplay.presentation.settings.devicename.DeviceNameScreen
import pl.prodevcode.tvairplay.presentation.settings.homekit.HomeKitScreen
import pl.prodevcode.tvairplay.presentation.settings.idledim.IdleDimScreen
import pl.prodevcode.tvairplay.presentation.settings.language.LanguageScreen
import pl.prodevcode.tvairplay.presentation.settings.latency.LatencyScreen
import pl.prodevcode.tvairplay.presentation.settings.trusted.TrustedDevicesScreen

private enum class Destination {
    Receiver, Settings, Licenses, Diagnostics, DeviceName, Language, IdleDim, Latency, TrustedDevices, HomeKit
}

private fun SettingsSub.toDestination() = when (this) {
    SettingsSub.DEVICE_NAME -> Destination.DeviceName
    SettingsSub.LANGUAGE -> Destination.Language
    SettingsSub.IDLE_DIM -> Destination.IdleDim
    SettingsSub.LATENCY -> Destination.Latency
    SettingsSub.TRUSTED_DEVICES -> Destination.TrustedDevices
    SettingsSub.HOMEKIT -> Destination.HomeKit
    SettingsSub.DIAGNOSTICS -> Destination.Diagnostics
    SettingsSub.LICENSES -> Destination.Licenses
}

@Composable
fun AppNavigation(receiverViewModel: ReceiverViewModel = hiltViewModel()) {
    var destination by rememberSaveable { mutableStateOf(Destination.Receiver) }
    val receiverUi by receiverViewModel.state.collectAsStateWithLifecycle()

    // sub-screens handle Back themselves; the settings list is the only one that falls through to here
    BackHandler(enabled = destination == Destination.Settings) { destination = Destination.Receiver }

    // a session that starts while the user is in Settings must show up, not play behind the menu
    val mode = receiverUi.receiver.mode
    LaunchedEffect(mode) {
        if (mode == SessionMode.MIRRORING || mode == SessionMode.VIDEO || mode == SessionMode.AUDIO) destination = Destination.Receiver
    }

    Box(Modifier.fillMaxSize()) {
        Screens(destination, onNavigate = { destination = it })
        // the pin must be visible wherever the user is, not only on the receiver screen
        receiverUi.receiver.pin?.let {
            PinOverlay(request = it, onDismiss = { receiverViewModel.onIntent(ReceiverIntent.DismissPin) })
        }
    }
}

@Composable
private fun Screens(destination: Destination, onNavigate: (Destination) -> Unit) {
    // keeps per-screen rememberSaveable state (e.g. the settings list scroll) while a sub-screen is shown
    val stateHolder = rememberSaveableStateHolder()
    val backToSettings = { onNavigate(Destination.Settings) }
    AnimatedContent(destination, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nav") { dest ->
        stateHolder.SaveableStateProvider(dest) {
            when (dest) {
                Destination.Receiver -> ReceiverScreen(onOpenSettings = { onNavigate(Destination.Settings) })
                Destination.Settings -> SettingsScreen(
                    onBack = { onNavigate(Destination.Receiver) },
                    onOpen = { onNavigate(it.toDestination()) },
                )
                Destination.Licenses -> LicensesScreen(onBack = backToSettings)
                Destination.Diagnostics -> DiagnosticsScreen(onBack = backToSettings)
                Destination.DeviceName -> DeviceNameScreen(onBack = backToSettings)
                Destination.Language -> LanguageScreen(onBack = backToSettings)
                Destination.IdleDim -> IdleDimScreen(onBack = backToSettings)
                Destination.Latency -> LatencyScreen(onBack = backToSettings)
                Destination.TrustedDevices -> TrustedDevicesScreen(onBack = backToSettings)
                Destination.HomeKit -> HomeKitScreen(onBack = backToSettings)
            }
        }
    }
}
