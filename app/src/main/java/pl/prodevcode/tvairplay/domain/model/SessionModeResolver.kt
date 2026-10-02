package pl.prodevcode.tvairplay.domain.model

/** Pure mapping of raw receiver signals to the single mode the UI renders. Video wins over mirroring, mirroring over audio. */
object SessionModeResolver {
    fun resolve(clients: Int, mirroring: Boolean, video: Boolean, audioOnly: Boolean): SessionMode = when {
        clients <= 0 -> SessionMode.IDLE
        video -> SessionMode.VIDEO
        mirroring -> SessionMode.MIRRORING
        audioOnly -> SessionMode.AUDIO
        else -> SessionMode.CONNECTED
    }
}
