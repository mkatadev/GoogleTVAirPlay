package pl.prodevcode.airplay.service

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundAudioSessionTest {
    private val receiver = "pl.prodevcode.tvairplay"
    private val youtube = "com.google.android.youtube.tv"

    @Test fun `actual app change disconnects even a paused audio session`() = runTest {
        val foreground = MutableStateFlow<String?>(receiver)
        var connections = 1
        var disconnects = 0
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            foreground.disconnectAudioOnAppChange(receiver, { true }, { connections }) {
                disconnects++
                connections = 0
            }
        }
        foreground.value = youtube
        runCurrent()
        assertEquals(1, disconnects)
        foreground.value = "com.netflix.ninja"
        runCurrent()
        assertEquals(1, disconnects)
    }

    @Test fun `cached external app does not disconnect a newly started receiver`() = runTest {
        val foreground = MutableStateFlow<String?>(youtube)
        var disconnects = 0
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            foreground.disconnectAudioOnAppChange(receiver, { true }, { 1 }) { disconnects++ }
        }
        runCurrent()
        assertEquals(0, disconnects)
        foreground.value = receiver
        runCurrent()
        assertEquals(0, disconnects)
        foreground.value = youtube
        runCurrent()
        assertEquals(1, disconnects)
    }

    @Test fun `observer reads current session state when a foreground event arrives`() = runTest {
        val foreground = MutableStateFlow<String?>(receiver)
        var audioOnly = false
        var disconnects = 0
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            foreground.disconnectAudioOnAppChange(receiver, { audioOnly }, { 1 }) { disconnects++ }
        }
        foreground.value = youtube
        runCurrent()
        assertEquals(0, disconnects)
        foreground.value = receiver
        runCurrent()
        audioOnly = true
        foreground.value = youtube
        runCurrent()
        assertEquals(1, disconnects)
    }
}
