package pl.prodevcode.tvairplay.data.receiver

import android.view.Surface
import javax.inject.Inject
import javax.inject.Singleton
import pl.prodevcode.tvairplay.platform.VideoSurfaceHost

@Singleton
class ServiceVideoSurfaceHost @Inject constructor(
    private val connector: AirPlayServiceConnector,
) : VideoSurfaceHost {
    private val svc get() = connector.service.value

    override fun attachMirrorSurface(surface: Surface) { svc?.setVideoSurface(surface) }
    override fun detachMirrorSurface(surface: Surface) { svc?.clearVideoSurface(surface) }
    override fun attachVideoSurface(surface: Surface) { svc?.setVideoPlaybackSurface(surface) }
    override fun detachVideoSurface(surface: Surface) { svc?.clearVideoPlaybackSurface(surface) }
}
