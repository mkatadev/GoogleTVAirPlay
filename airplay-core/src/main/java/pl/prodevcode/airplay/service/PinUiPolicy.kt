package pl.prodevcode.airplay.service

internal fun shouldBringPinUiToFront(
    requested: PinPrompt,
    current: PinPrompt?,
    nowMs: Long,
    launchOnConnect: Boolean,
    uiDismissed: Boolean,
): Boolean = launchOnConnect &&
    !uiDismissed &&
    requested == current &&
    nowMs < requested.expiresAtMs
