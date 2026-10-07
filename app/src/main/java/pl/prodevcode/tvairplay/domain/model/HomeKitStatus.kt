package pl.prodevcode.tvairplay.domain.model

/** Apple Home side of the receiver; `setupCode` is only present while unpaired and advertising. */
data class HomeKitStatus(
    val running: Boolean = false,
    val paired: Boolean = false,
    val controllers: Int = 0,
    val setupCode: String? = null,
    /** `X-HM://` payload to render as a QR code; present together with [setupCode]. */
    val setupUri: String? = null,
    val accessoryId: String = "",
    /** The TV-remote accessibility service is on: Home controls Google TV itself, not only the receiver. */
    val tvControl: Boolean = false,
)
