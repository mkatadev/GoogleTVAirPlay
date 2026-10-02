package pl.prodevcode.tvairplay.presentation.receiver

import pl.prodevcode.tvairplay.domain.model.DeviceInfo
import pl.prodevcode.tvairplay.domain.model.ReceiverState

data class ReceiverUiState(
    val receiver: ReceiverState = ReceiverState(),
    val device: DeviceInfo = DeviceInfo(name = "", ipAddress = null),
    val keepScreenOn: Boolean = false,
    val runInBackground: Boolean = true,
    val overlayPermissionGranted: Boolean = true,
)
