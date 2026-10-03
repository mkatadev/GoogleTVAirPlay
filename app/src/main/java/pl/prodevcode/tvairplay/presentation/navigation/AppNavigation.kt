package pl.prodevcode.tvairplay.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import pl.prodevcode.tvairplay.presentation.diagnostics.DiagnosticsScreen
import pl.prodevcode.tvairplay.presentation.licenses.LicensesScreen
import pl.prodevcode.tvairplay.presentation.receiver.ReceiverScreen
import pl.prodevcode.tvairplay.presentation.settings.SettingsScreen

private enum class Destination { Receiver, Settings, Licenses, Diagnostics }

@Composable
fun AppNavigation() {
    var destination by rememberSaveable { mutableStateOf(Destination.Receiver) }

    BackHandler(enabled = destination == Destination.Settings) { destination = Destination.Receiver }

    AnimatedContent(destination, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nav") { dest ->
        when (dest) {
            Destination.Receiver -> ReceiverScreen(onOpenSettings = { destination = Destination.Settings })
            Destination.Settings -> SettingsScreen(
                onBack = { destination = Destination.Receiver },
                onOpenLicenses = { destination = Destination.Licenses },
                onOpenDiagnostics = { destination = Destination.Diagnostics },
            )
            Destination.Licenses -> LicensesScreen(onBack = { destination = Destination.Settings })
            Destination.Diagnostics -> DiagnosticsScreen(onBack = { destination = Destination.Settings })
        }
    }
}
