package pl.prodevcode.tvairplay.domain.model

/** Apple Home side of the receiver; `setupCode` is only present while unpaired and advertising. */
data class HomeKitStatus(
    val running: Boolean = false,
    val paired: Boolean = false,
    val controllers: Int = 0,
    val setupCode: String? = null,
    val accessoryId: String = "",
)
