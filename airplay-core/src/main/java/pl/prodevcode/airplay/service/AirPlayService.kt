package pl.prodevcode.airplay.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.view.Surface
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import pl.prodevcode.airplay.Prefs
import pl.prodevcode.airplay.audio.DacpController
import pl.prodevcode.airplay.audio.DacpPlayer
import pl.prodevcode.airplay.audio.NowPlayingState
import pl.prodevcode.airplay.audio.VolumeSync
import pl.prodevcode.airplay.bridge.LogListener
import pl.prodevcode.airplay.bridge.NativeBridge
import pl.prodevcode.airplay.bridge.RaopCallbackHandler
import pl.prodevcode.airplay.discovery.NetworkWatcher
import pl.prodevcode.airplay.discovery.NsdServiceManager
import pl.prodevcode.airplay.model.DebugInfo
import pl.prodevcode.airplay.realDisplaySize
import pl.prodevcode.airplay.renderer.AudioRenderer
import pl.prodevcode.airplay.renderer.VideoRenderer
import pl.prodevcode.airplay.security.TrustedDeviceStore

/**
 * Foreground service hosting the native AirPlay receiver. Owns the lifecycle (start/stop,
 * wake lock, mDNS) and dispatches native callbacks to the session collaborators:
 * [VideoSession] (HLS video), [NowPlayingState] (audio metadata), [VolumeSync],
 * [MediaSessionController] and [ServiceNotifications].
 */
class AirPlayService : LifecycleService(), RaopCallbackHandler, LogListener {

    private var nativeHandle = 0L
    private var nsdManager: NsdServiceManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastOrientation = Configuration.ORIENTATION_UNDEFINED

    val videoRenderer = VideoRenderer(this)
    val audioRenderer = AudioRenderer()
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val prefs: SharedPreferences by lazy { getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE) }
    private val mainHandler = Handler(Looper.getMainLooper())

    val video = VideoSession(this)
    val nowPlaying = NowPlayingState()
    val trustedDevices by lazy { TrustedDeviceStore(prefs) }
    private lateinit var volumeSync: VolumeSync
    private lateinit var mediaSession: MediaSessionController
    private lateinit var notifications: ServiceNotifications
    private lateinit var networkWatcher: NetworkWatcher
    private var nsdStatusJob: Job? = null

    var dacpController: DacpController? = null
        private set
    lateinit var dacpPlayer: DacpPlayer
        private set
    private var mediaReceiver: BroadcastReceiver? = null

    // --- state exposed to the app -------------------------------------------------------------------

    private val _serverState = MutableStateFlow(ServerState.STOPPED)
    val serverState = _serverState.asStateFlow()

    private val _connectionCount = MutableStateFlow(0)
    val connectionCount = _connectionCount.asStateFlow()

    private val _videoAspect = MutableStateFlow(16f / 9f)
    val videoAspect = _videoAspect.asStateFlow()

    private val _videoResolution = MutableStateFlow("")
    val videoResolution = _videoResolution.asStateFlow()

    private val _audioOnly = MutableStateFlow(false)
    val audioOnly = _audioOnly.asStateFlow()

    // set once mirroring reports a real size; stops with session
    private val _mirroringActive = MutableStateFlow(false)
    val mirroringActive = _mirroringActive.asStateFlow()

    private val _nsdStatus = MutableStateFlow(NsdServiceManager.Status())
    val nsdStatus = _nsdStatus.asStateFlow()

    /** Port the receiver listens on, 0 when stopped. */
    private val _port = MutableStateFlow(0)
    val port = _port.asStateFlow()

    /** Why the last start failed; cleared on the next successful start. */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError = _lastError.asStateFlow()

    val networkStatus get() = networkWatcher.status

    // flattened aliases kept for existing consumers
    val videoPlaybackActive get() = video.active
    val videoPlaybackInfo get() = video.info
    val videoPlaybackAspect get() = video.aspect
    val videoTitle get() = video.title
    val trackInfo get() = nowPlaying.track
    val positionMs get() = nowPlaying.positionMs
    val durationMs get() = nowPlaying.durationMs
    val playing get() = nowPlaying.playing

    fun videoSessionPending(): Boolean = video.pending(otherSessionActive = _audioOnly.value || _mirroringActive.value)
    fun currentPositionMs(): Long = nowPlaying.currentPositionMs()

    var logCallback: ((String) -> Unit)? = null

    @Volatile private var lastPin: String? = null
    var pinCallback: ((String?) -> Unit)? = null
        set(value) {
            field = value
            // ui replay only: binding the activity must not mint a new native pin
            value?.invoke(lastPin)
        }

    private fun log(msg: String) {
        Log.i(TAG, msg)
        logCallback?.invoke(msg)
    }

    override fun onLog(msg: String) {
        logCallback?.invoke(msg)
    }

    inner class LocalBinder : Binder() {
        val service: AirPlayService get() = this@AirPlayService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return LocalBinder()
    }

    // --- lifecycle ---------------------------------------------------------------------------------

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        notifications = ServiceNotifications(this) {
            ServiceNotifications.Snapshot(
                track = nowPlaying.track.value,
                audioOnly = _audioOnly.value,
                pin = lastPin,
                sessionToken = mediaSession.token,
            )
        }
        notifications.createChannel()
        dacpController = DacpController(this)
        dacpPlayer = DacpPlayer(
            mainLooper,
            dacp = { dacpController },
            snapshot = {
                DacpPlayer.Snapshot(
                    track = nowPlaying.track.value,
                    artworkData = nowPlaying.coverArtBytes,
                    durationMs = nowPlaying.durationMs.value,
                    playing = nowPlaying.playing.value,
                    active = _audioOnly.value && _connectionCount.value > 0,
                )
            },
            positionMs = nowPlaying::currentPositionMs,
            setPlaying = ::setPlaying,
        )
        mediaSession = MediaSessionController(this, mediaSessionCallback())
        volumeSync = VolumeSync(this, audioManager, dacp = { dacpController }, sessionActive = { _connectionCount.value > 0 })
        volumeSync.start()
        networkWatcher = NetworkWatcher(this) { addrs ->
            if (_serverState.value != ServerState.RUNNING) return@NetworkWatcher
            log("Network changed (${addrs.joinToString()}), re-announcing AirPlay")
            nsdManager?.reregister()
        }

        mediaReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_PLAY_PAUSE -> togglePlayPause()
                    ACTION_NEXT -> dacpController?.nextItem()
                    ACTION_PREV -> dacpController?.prevItem()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_NEXT)
            addAction(ACTION_PREV)
        }
        ContextCompat.registerReceiver(this, mediaReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        video.bind(
            onEnded = { endVideoPlayback("AirPlay Video stopped (player)") },
            onPlaybackInfo = { snapshot ->
                if (nativeHandle != 0L) {
                    NativeBridge.nativeUpdatePlaybackInfo(nativeHandle, snapshot.position, snapshot.duration, snapshot.rate, snapshot.ready)
                }
                if (video.active.value) mediaSession.setVideoState(snapshot.position, snapshot.rate)
            },
        )
        lifecycleScope.launch {
            prefs.audioConfigFlow()
                .debounce(AUDIO_CONFIG_DEBOUNCE_MS)
                .collect { audioRenderer.updateConfig(it) }
        }
    }

    private fun mediaSessionCallback() = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            if (video.active.value) { video.setPlaying(true); return }
            setPlaying(true)
            dacpController?.play()
        }
        override fun onPause() {
            if (video.active.value) { video.setPlaying(false); return }
            setPlaying(false)
            dacpController?.pause()
        }
        override fun onStop() { if (video.active.value) stopVideoPlayback() }
        override fun onFastForward() { if (video.active.value) video.seekBy(VIDEO_SEEK_STEP_MS) }
        override fun onRewind() { if (video.active.value) video.seekBy(-VIDEO_SEEK_STEP_MS) }
        override fun onSeekTo(pos: Long) { if (video.active.value) video.scrub(pos / 1000f) }
        override fun onSkipToNext() { if (!video.active.value) dacpController?.nextItem() }
        override fun onSkipToPrevious() { if (!video.active.value) dacpController?.prevItem() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_SERVER) {
            notifications.promoteToForeground()
            val name = prefs.getString(Prefs.SERVER_NAME, Prefs.DEF_SERVER_NAME) ?: Prefs.DEF_SERVER_NAME
            startServer(name, ensureServiceStarted = false)
            if (_serverState.value != ServerState.RUNNING) stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopServer()
        dacpPlayer.release()
        mediaReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        mediaReceiver = null
        volumeSync.release()
        dacpController?.release()
        dacpController = null
        mediaSession.release()
        super.onDestroy()
    }

    // --- server ------------------------------------------------------------------------------------

    fun startServer(name: String) = startServer(name, ensureServiceStarted = true)

    private fun startServer(name: String, ensureServiceStarted: Boolean) {
        if (_serverState.value == ServerState.RUNNING) return
        val effectiveName = name.ifBlank { Prefs.DEF_SERVER_NAME }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "airplay:server").apply { acquire() }

        nsdManager = NsdServiceManager(this).apply { acquireMulticastLock() }

        val hwAddr = DeviceIdentity(prefs).hardwareAddress()
        val keyFile = filesDir.resolve("airplay.pem").absolutePath
        val nohold = prefs.getBoolean(Prefs.ALLOW_NEW_CONN, Prefs.DEF_ALLOW_NEW_CONN)
        val requirePin = prefs.getBoolean(Prefs.REQUIRE_PIN, Prefs.DEF_REQUIRE_PIN)

        // oboe's OpenSL ES backend (pre-AAudio devices, API < 27) can't discover native
        // rate / burst size itself; feed it AudioManager values so low-latency buffer
        // sizing works there
        // see: https://github.com/google/oboe/blob/main/docs/GettingStarted.md#obtaining-optimal-latency
        NativeBridge.nativeSetDefaultStreamValues(
            audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 0,
            audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 0
        )
        nativeHandle = NativeBridge.nativeInit(this, hwAddr, effectiveName, keyFile, nohold, requirePin)
        if (nativeHandle == 0L) {
            failStart("Native init failed")
            return
        }
        audioRenderer.attachEngine(nativeHandle)

        // apply settings from preferences
        val maxFps = prefs.getInt(Prefs.MAX_FPS, Prefs.DEF_MAX_FPS)
        val overscanned = prefs.getBoolean(Prefs.OVERSCANNED, Prefs.DEF_OVERSCANNED)
        val audioLatencyMs = prefs.getInt(Prefs.AUDIO_LATENCY_MS, Prefs.DEF_AUDIO_LATENCY_MS)
        val (reqW, reqH) = displaySize(clamp = false)
        val h265 = videoRenderer.selectDecoders(reqW, reqH, maxFps, prefs.getBoolean(Prefs.H265_ENABLED, Prefs.DEF_H265_ENABLED))
        val alac = prefs.getBoolean(Prefs.ALAC_ENABLED, Prefs.DEF_ALAC_ENABLED)
        val aac = prefs.getBoolean(Prefs.AAC_ENABLED, Prefs.DEF_AAC_ENABLED)

        videoRenderer.enforceSdr = prefs.getBoolean(Prefs.ENFORCE_SDR, Prefs.DEF_ENFORCE_SDR)
        videoRenderer.keyAllowFrameDrop = prefs.getBoolean(Prefs.KEY_ALLOW_FRAME_DROP, Prefs.DEF_KEY_ALLOW_FRAME_DROP)
        videoRenderer.selector.maxOperatingRate = when (prefs.getString(Prefs.OPERATING_RATE, Prefs.DEF_OPERATING_RATE)) {
            Prefs.ON -> true; Prefs.OFF -> false; else -> null
        }
        videoRenderer.benchmarkLog = prefs.getBoolean(Prefs.BENCHMARK_LOG, Prefs.DEF_BENCHMARK_LOG)
        videoRenderer.benchmarkLogCallback = { msg -> logCallback?.invoke(msg) }
        videoRenderer.scheduledOutputBufferRelease = prefs.getBoolean(Prefs.SCHEDULED_OUTPUT_BUFFER_RELEASE, Prefs.DEF_SCHEDULED_OUTPUT_BUFFER_RELEASE)
        NativeBridge.nativeSetH265Enabled(nativeHandle, h265)
        NativeBridge.nativeSetCodecs(nativeHandle, alac, aac)
        val advertiseVideo = prefs.getBoolean(Prefs.ADVERTISE_VIDEO, Prefs.DEF_ADVERTISE_VIDEO)
        val advertiseAudio = prefs.getBoolean(Prefs.ADVERTISE_AUDIO, Prefs.DEF_ADVERTISE_AUDIO)
        NativeBridge.nativeSetHlsEnabled(nativeHandle, advertiseVideo)
        NativeBridge.nativeSetLang(nativeHandle, "", "", resources.configuration.locales.toLanguageTags().replace(',', ':'))
        NativeBridge.nativeSetAudioEnabled(nativeHandle, advertiseAudio)
        NativeBridge.nativeSetPlist(nativeHandle, "maxFPS", maxFps)
        NativeBridge.nativeSetPlist(nativeHandle, "overscanned", if (overscanned) 1 else 0)
        if (audioLatencyMs >= 0) {
            NativeBridge.nativeSetPlist(nativeHandle, "audio_delay_micros", audioLatencyMs * 1000)
        }

        // set display params
        lastOrientation = resources.configuration.orientation
        val (w, h) = displaySize()
        videoRenderer.setResolution(w, h)
        _videoResolution.value = "${w}x${h}"
        _videoAspect.value = w.toFloat() / h
        NativeBridge.nativeSetDisplaySize(nativeHandle, w, h, maxFps)

        val requestedPort = prefs.getInt(Prefs.SERVER_PORT, Prefs.DEF_SERVER_PORT).coerceIn(1, 65535)
        val port = NativeBridge.nativeStart(nativeHandle, requestedPort)
        if (port < 0) {
            failStart("Failed to start on port $requestedPort")
            return
        }
        _port.value = port
        _lastError.value = null

        // register mdns services
        val raopTxt = NativeBridge.nativeGetRaopTxtRecords(nativeHandle) ?: emptyMap()
        val airplayTxt = NativeBridge.nativeGetAirplayTxtRecords(nativeHandle) ?: emptyMap()
        val raopName = NativeBridge.nativeGetRaopServiceName(nativeHandle) ?: "AirPlay"
        val resolvedName = NativeBridge.nativeGetServerName(nativeHandle) ?: effectiveName

        nsdManager?.let { nsd ->
            if (advertiseAudio) nsd.registerRaop(raopName, port, raopTxt)
            nsd.registerAirplay(resolvedName, port, airplayTxt)
            nsdStatusJob = lifecycleScope.launch { nsd.status.collect { _nsdStatus.value = it } }
        }
        networkWatcher.start()

        _serverState.value = ServerState.RUNNING
        if (ensureServiceStarted) {
            ContextCompat.startForegroundService(this, Intent(this, AirPlayService::class.java))
        }
        notifications.promoteToForeground()
        log("Server started on port $port")
    }

    private fun releaseServerResources() {
        audioRenderer.detachEngine()
        if (nativeHandle != 0L) {
            NativeBridge.nativeStop(nativeHandle)
            NativeBridge.nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
        networkWatcher.stop()
        nsdStatusJob?.cancel()
        nsdStatusJob = null
        _nsdStatus.value = NsdServiceManager.Status()
        nsdManager?.release()
        nsdManager = null
        wakeLock?.release()
        wakeLock = null
        _port.value = 0
    }

    private fun failStart(reason: String) {
        log(reason)
        _lastError.value = reason
        releaseServerResources()
        _serverState.value = ServerState.ERROR
        notifications.dismiss()
    }

    fun stopServer() {
        releaseServerResources()
        dacpController?.reset()
        videoRenderer.release()
        video.reset()
        mediaSession.active = false
        _audioOnly.value = false
        _mirroringActive.value = false
        nowPlaying.clear()
        _serverState.value = ServerState.STOPPED
        _connectionCount.value = 0
        refreshDacpPlayer()
        notifications.dismiss()
        stopSelf()
        log("Server stopped")
    }

    private fun orientationFollowsDevice(): Boolean =
        prefs.getString(Prefs.RESOLUTION, Prefs.DEF_RESOLUTION) == Prefs.AUTO

    private fun displaySize(clamp: Boolean = true): Pair<Int, Int> {
        val res = prefs.getString(Prefs.RESOLUTION, Prefs.DEF_RESOLUTION)!!
        val portrait = when (res) {
            "portrait" -> true
            "landscape" -> false
            else -> resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        }
        val (rawW, rawH) = realDisplaySize()
        val device = if (portrait != rawH >= rawW) rawH to rawW else rawW to rawH
        if (res == "portrait" || res == "landscape") return device
        val (w, h) = if (res.contains("x")) {
            res.split("x").let { it[0].toInt() to it[1].toInt() }
        } else device
        if (!clamp) return w to h
        // strict decoders black-screen past their limits; advertised size is only upper bound for senders
        val (maxW, maxH) = videoRenderer.maxResolution()
        return w.coerceAtMost(maxW) to h.coerceAtMost(maxH)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == lastOrientation) return
        lastOrientation = newConfig.orientation
        if (nativeHandle == 0L || _serverState.value != ServerState.RUNNING) return
        if (!orientationFollowsDevice()) return
        val (w, h) = displaySize()
        NativeBridge.nativeSetDisplaySize(nativeHandle, w, h, prefs.getInt(Prefs.MAX_FPS, Prefs.DEF_MAX_FPS))
        log("Advertising ${w}x${h} from next session")
    }

    // --- surfaces & transport (called by the app) ---------------------------------------------------

    fun setVideoSurface(surface: Surface) = videoRenderer.setSurface(surface)
    fun clearVideoSurface(surface: Surface) = videoRenderer.clearSurface(surface)
    fun setVideoPlaybackSurface(surface: Surface) = video.setSurface(surface)
    fun clearVideoPlaybackSurface(surface: Surface) = video.clearSurface(surface)
    fun setVideoPlaying(playing: Boolean) = video.setPlaying(playing)
    fun seekVideoTo(positionMs: Long) = video.scrub(positionMs / 1000f)
    fun setVideoScrubbing(enabled: Boolean) = video.setScrubbing(enabled)
    fun setVideoSpeed(speed: Float) = video.setSpeed(speed)
    fun setVideoSkipSilence(enabled: Boolean) = video.setSkipSilence(enabled)
    fun seekVideoBy(deltaMs: Long) = video.seekBy(deltaMs)
    fun stopVideoPlayback() = endVideoPlayback("AirPlay Video stopped (local)")

    fun togglePlayPause() {
        val playing = !nowPlaying.playing.value
        setPlaying(playing)
        dacpController?.let { if (playing) it.play() else it.pause() }
    }

    private fun endVideoPlayback(message: String) {
        if (!video.end()) return
        if (!_audioOnly.value) mediaSession.active = false
        log(message)
    }

    // --- RaopCallbackHandler (called from native threads) -----------------------------------------

    override fun onVideoData(data: ByteArray, ntpTimeNs: Long, isH265: Boolean) {
        videoRenderer.feedFrame(data, ntpTimeNs, isH265)
    }

    override fun onVideoSessionPoll() = video.onPoll()

    override fun onVideoPlay(location: String, startPositionSeconds: Float) {
        video.play(location, startPositionSeconds)
        bringUiToFront()
        // claim media-button routing for keys that arrive as media-session events
        mediaSession.active = true
        log("AirPlay Video play: $location @ ${startPositionSeconds}s")
    }

    override fun onVideoScrub(positionSeconds: Float) = video.scrub(positionSeconds)
    override fun onVideoRate(rate: Float) = video.setRate(rate)
    override fun onVideoStop() = endVideoPlayback("AirPlay Video stopped")

    override fun onAudioFormat(ct: Int, spf: Int, usingScreen: Boolean) {
        clearPin()
        audioRenderer.start()
        audioRenderer.setFormat(ct, spf)
        if (!usingScreen) setPlaying(true)
        if (!usingScreen && !_audioOnly.value) {
            // pure music streaming (not screen mirroring audio)
            setAudioOnly(true)
            bringUiToFront()
        }
        log("Audio format: ct=$ct spf=$spf screen=$usingScreen")
    }

    override fun onVideoSize(srcW: Float, srcH: Float, w: Float, h: Float) {
        clearPin()
        if (w > 0 && h > 0) {
            _videoAspect.value = w / h
            _videoResolution.value = "${w.toInt()}x${h.toInt()}"
            videoRenderer.setResolution(w.toInt(), h.toInt())
            _mirroringActive.value = true
        }
        log("Video size: ${srcW}x${srcH} -> ${w}x${h}")
    }

    override fun onVolumeChange(volume: Float) = volumeSync.onSenderVolume(volume)
    override fun onClientVolume(): Float = volumeSync.clientVolumeDb()

    override fun onConnectionInit() {
        val firstConnection = _connectionCount.value == 0
        _connectionCount.value++
        log("Client connected (${_connectionCount.value})")
        if (!firstConnection) return
        // conn_init is only a tcp pre-auth signal. pin-required sessions must wait for
        // onDisplayPin, otherwise the server ui can move before the client pin is current
        if (requiresPin()) return
        if (!shouldLaunchOnConnect()) return
        launchMainActivity()
    }

    override fun onConnectionDestroy() {
        _connectionCount.value = (_connectionCount.value - 1).coerceAtLeast(0)
        if (_connectionCount.value == 0) {
            // clients may drop without POST /stop; must run before the poll-state reset
            endVideoPlayback("AirPlay Video stopped (disconnect)")
            // last client gone: release audio output devices to save power
            audioRenderer.stop()
            _audioOnly.value = false
            video.onAllClientsGone()
            nowPlaying.clear()
            dacpController?.reset()
            volumeSync.onSessionEnded()
            mediaSession.active = false
            refreshDacpPlayer()
            updateNotification()
        }
        log("Client disconnected (${_connectionCount.value})")
    }

    override fun onConnectionReset(reason: Int) {
        log("Connection reset: $reason")
    }

    override fun onDisplayPin(pin: String) {
        // a new pin is the sync point with the client prompt: show every new value immediately
        if (lastPin == pin) return
        lastPin = pin
        pinCallback?.invoke(pin)
        updateNotification()
    }

    override fun onMetadata(data: ByteArray) {
        val info = nowPlaying.onMetadata(data)
        updateMediaMetadata()
        refreshDacpPlayer()
        log("Track: ${info.artist} - ${info.title}")
    }

    override fun onCoverArt(data: ByteArray) {
        if (!nowPlaying.onCoverArt(data)) return
        updateMediaMetadata()
        refreshDacpPlayer()
    }

    override fun onProgress(start: Long, curr: Long, end: Long) {
        // pause/resume transitions emit degenerate progress; keep the last good value
        if (!nowPlaying.onProgress(start, curr, end)) return
        updatePlaybackState()
        refreshDacpPlayer()
    }

    override fun onAudioTeardown() = setPlaying(false)

    override fun onDacpId(dacpId: String, activeRemote: String) {
        dacpController?.update(dacpId, activeRemote)
        log("DACP: $dacpId")
    }

    override fun onClientRegistered(deviceId: String, publicKey: String, name: String) {
        trustedDevices.register(deviceId, publicKey, name)
        log("Paired: ${name.ifBlank { deviceId }}")
    }

    override fun isClientRegistered(publicKey: String): Boolean = trustedDevices.isTrusted(publicKey)

    override fun onMirrorRunning(running: Boolean) {
        if (running) bringUiToFront()
        if (running) videoRenderer.startSession() else {
            videoRenderer.stopSession()
            _mirroringActive.value = false
        }
        setAudioOnly(!running)
    }

    // --- session bookkeeping -----------------------------------------------------------------------

    private fun setAudioOnly(audioOnly: Boolean) {
        val prev = _audioOnly.value
        _audioOnly.value = audioOnly
        refreshDacpPlayer()
        if (audioOnly && !prev) {
            mediaSession.active = true
            log("Audio mode")
        } else if (!audioOnly && prev) {
            mediaSession.active = false
            nowPlaying.clear()
            updateNotification()
            log("Mirror mode")
        }
    }

    private fun setPlaying(playing: Boolean) {
        nowPlaying.setPlaying(playing)
        refreshDacpPlayer()
        updatePlaybackState()
    }

    private fun updateMediaMetadata() {
        mediaSession.setMetadata(nowPlaying.track.value, nowPlaying.durationMs.value)
        updateNotification()
    }

    private fun updatePlaybackState() {
        // the sender's silent raop audio session must not overwrite video session state
        if (video.active.value) return
        mediaSession.setAudioState(nowPlaying.playing.value, nowPlaying.positionMs.value)
        updateNotification()
    }

    private fun refreshDacpPlayer() {
        mainHandler.post { dacpPlayer.refresh() }
    }

    private fun clearPin() {
        lastPin = null
        pinCallback?.invoke(null)
        updateNotification()
    }

    private fun updateNotification() {
        if (_serverState.value == ServerState.RUNNING) notifications.update()
    }

    fun collectDebugInfo() = DebugInfo(
        videoCodec = videoRenderer.codecName,
        videoRes = _videoResolution.value,
        videoFps = videoRenderer.fps,
        videoBitrate = videoRenderer.bitrateBps,
        videoFrames = videoRenderer.frameCount,
        droppedFrames = videoRenderer.droppedFrames,
        framePacingJitterUs = videoRenderer.framePacingJitterUs,
        audioCodec = audioRenderer.codecLabel,
        audioVolume = volumeSync.volumePercent(),
        audio = audioRenderer.audioDebug(),
        connections = _connectionCount.value,
    )

    // --- ui hand-off -------------------------------------------------------------------------------

    private fun requiresPin(): Boolean = prefs.getBoolean(Prefs.REQUIRE_PIN, Prefs.DEF_REQUIRE_PIN)
    private fun shouldLaunchOnConnect(): Boolean = prefs.getBoolean(Prefs.LAUNCH_ON_CONNECT, Prefs.DEF_LAUNCH_ON_CONNECT)

    /** Media started: show the receiver UI (needs SYSTEM_ALERT_WINDOW on TV when we are in the background). */
    private fun bringUiToFront() {
        if (!shouldLaunchOnConnect()) return
        if (requiresPin() && lastPin != null) return
        launchMainActivity()
    }

    private fun launchMainActivity() {
        mainHandler.post {
            val launchIntent = notifications.launcherIntent()
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            try {
                startActivity(launchIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch activity", e)
            }
        }
    }

    enum class ServerState { STOPPED, RUNNING, ERROR }

    companion object {
        private const val TAG = "AirPlayService"
        const val ACTION_PLAY_PAUSE = "pl.prodevcode.airplay.PLAY_PAUSE"
        const val ACTION_NEXT = "pl.prodevcode.airplay.NEXT"
        const val ACTION_PREV = "pl.prodevcode.airplay.PREV"
        const val ACTION_START_SERVER = "pl.prodevcode.airplay.START_SERVER"
        // shared with dpad/double-tap seeks
        const val VIDEO_SEEK_STEP_MS = 10_000L

        private const val AUDIO_CONFIG_DEBOUNCE_MS = 500L
    }
}
