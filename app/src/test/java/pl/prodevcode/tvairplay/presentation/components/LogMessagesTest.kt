package pl.prodevcode.tvairplay.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.prodevcode.tvairplay.R

class LogMessagesTest {
    @Test fun `known service lines map to resources with their arguments`() {
        LogMessages.match("Server started on port 7000")!!.let { assertEquals(R.string.log_server_started, it.res); assertEquals(listOf(7000), it.args) }
        LogMessages.match("Client connected (2)")!!.let { assertEquals(R.string.log_client_connected, it.res); assertEquals(listOf(2), it.args) }
        LogMessages.match("Paired: iPhone (Mateusz)")!!.let { assertEquals(R.string.log_paired, it.res); assertEquals(listOf("iPhone (Mateusz)"), it.args) }
        LogMessages.match("Last client gone, holding session 8s")!!.let { assertEquals(R.string.log_last_client_gone, it.res); assertEquals(listOf(8), it.args) }
        assertEquals(R.string.log_network_changed, LogMessages.match("Network changed (192.168.1.5), re-announcing AirPlay")!!.res)
        assertEquals(R.string.log_video_play, LogMessages.match("AirPlay Video play: http://localhost:7000/master.m3u8 @ 2.6s")!!.res)
        assertEquals(R.string.log_screen_off, LogMessages.match("Screen off, suspending receiver")!!.res)
    }

    @Test fun `technical lines pass through`() {
        assertNull(LogMessages.match("Audio format: ct=2 spf=352 screen=false"))
        assertNull(LogMessages.match("DACP: 4497C3C054CB0F6F"))
        assertNull(LogMessages.match(""))
    }
}
