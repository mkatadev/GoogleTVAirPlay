package pl.prodevcode.airplay.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop

internal suspend fun Flow<String?>.disconnectAudioOnAppChange(
    receiverPackage: String,
    audioOnly: () -> Boolean,
    connectionCount: () -> Int,
    disconnect: () -> Unit,
) {
    // A cached foreground package at subscription time is not a new app switch.
    drop(1).collect { front ->
        if (shouldDisconnectAirPlayAudio(front, receiverPackage, audioOnly(), connectionCount())) {
            disconnect()
        }
    }
}

internal fun shouldDisconnectAirPlayAudio(
    foregroundPackage: String?,
    receiverPackage: String,
    audioOnly: Boolean,
    connectionCount: Int,
): Boolean = foregroundPackage != null &&
    foregroundPackage != receiverPackage &&
    audioOnly &&
    connectionCount > 0
