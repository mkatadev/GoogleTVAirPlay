package pl.prodevcode.tvairplay.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionModeResolverTest {
    @Test fun `no clients is idle regardless of flags`() {
        assertEquals(SessionMode.IDLE, SessionModeResolver.resolve(0, mirroring = true, video = true, audioOnly = true))
    }

    @Test fun `video takes precedence over mirroring and audio`() {
        assertEquals(SessionMode.VIDEO, SessionModeResolver.resolve(1, mirroring = true, video = true, audioOnly = true))
    }

    @Test fun `mirroring takes precedence over audio`() {
        assertEquals(SessionMode.MIRRORING, SessionModeResolver.resolve(1, mirroring = true, video = false, audioOnly = true))
    }

    @Test fun `audio only session`() {
        assertEquals(SessionMode.AUDIO, SessionModeResolver.resolve(1, mirroring = false, video = false, audioOnly = true))
    }

    @Test fun `connected without stream yet`() {
        assertEquals(SessionMode.CONNECTED, SessionModeResolver.resolve(2, mirroring = false, video = false, audioOnly = false))
    }
}
