package pl.prodevcode.tvairplay.domain.model

data class ReceiverSettings(
    val deviceName: String = "Google TV",
    val startOnBoot: Boolean = true,
    val runInBackground: Boolean = true,
    val openAppOnConnect: Boolean = true,
    val requirePin: Boolean = true,
    /** Senders that entered the PIN once skip it next time. */
    val rememberDevices: Boolean = true,
    val hevcEnabled: Boolean = true,
    val advertiseVideo: Boolean = true,
    val advertiseAudio: Boolean = true,
)
