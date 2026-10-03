package pl.prodevcode.tvairplay.platform

import android.view.Surface

/**
 * Android-specific port kept out of the domain: hands the UI's [Surface]s to the receiver
 * so mirroring and AirPlay video can render into them.
 */
interface VideoSurfaceHost {
    fun attachMirrorSurface(surface: Surface)
    fun detachMirrorSurface(surface: Surface)
    fun attachVideoSurface(surface: Surface)
    fun detachVideoSurface(surface: Surface)
}
