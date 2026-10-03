package pl.prodevcode.tvairplay.domain.model

/** A sender that completed PIN pairing and may connect without a PIN until forgotten. */
data class TrustedDevice(
    val id: String,
    val name: String,
    val addedAt: Long,
    val lastSeenAt: Long,
)
