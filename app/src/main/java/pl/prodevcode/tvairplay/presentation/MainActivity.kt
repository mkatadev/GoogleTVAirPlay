package pl.prodevcode.tvairplay.presentation

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pl.prodevcode.tvairplay.presentation.navigation.AppNavigation
import pl.prodevcode.tvairplay.presentation.receiver.ReceiverIntent
import pl.prodevcode.tvairplay.presentation.receiver.ReceiverViewModel
import pl.prodevcode.tvairplay.presentation.theme.TvAirPlayTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val receiverViewModel: ReceiverViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                receiverViewModel.state.map { it.keepScreenOn }.distinctUntilChanged().collect { keep ->
                    if (keep) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        setContent {
            TvAirPlayTheme {
                AppNavigation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        receiverViewModel.onIntent(ReceiverIntent.AppResumed)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) receiverViewModel.onIntent(ReceiverIntent.AppBackgrounded)
    }
}
