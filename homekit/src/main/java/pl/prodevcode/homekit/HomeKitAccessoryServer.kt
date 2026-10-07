package pl.prodevcode.homekit

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import pl.prodevcode.homekit.accessory.AccessoryInfo
import pl.prodevcode.homekit.accessory.TelevisionAccessory
import pl.prodevcode.homekit.accessory.TelevisionControls
import pl.prodevcode.homekit.accessory.TvInput
import pl.prodevcode.homekit.pairing.PairingStore
import pl.prodevcode.homekit.pairing.SetupPayload
import pl.prodevcode.homekit.server.HapRouter
import pl.prodevcode.homekit.server.HapServer

/**
 * Exposes the receiver to Apple Home as a Television accessory: HAP server, pairing state and
 * mDNS advertisement. Create once per process, [start]/[stop] with the host's lifecycle and feed
 * receiver state through [television].
 */
class HomeKitAccessoryServer(
    context: Context,
    prefs: SharedPreferences,
    scope: CoroutineScope,
    private val info: AccessoryInfo,
    controls: TelevisionControls,
    inputs: List<TvInput> = listOf(TelevisionAccessory.DEFAULT_INPUT),
) {
    data class Status(
        val running: Boolean = false,
        /** At least one controller completed pair-setup. */
        val paired: Boolean = false,
        val controllers: Int = 0,
        /** Code to enter in the Home app; only while unpaired and running. */
        val setupCode: String? = null,
        /** `X-HM://` payload for a QR code the Home app can scan; present together with [setupCode]. */
        val setupUri: String? = null,
        val accessoryId: String = "",
        val port: Int = 0,
    )

    private val store = PairingStore(prefs)
    val television = TelevisionAccessory(info, controls, inputs)
    private val advertiser = HapAdvertiser(context)
    private var name = info.name

    private val _running = MutableStateFlow(false)
    private val _setupCode = MutableStateFlow<String?>(null)
    private val _port = MutableStateFlow(0)

    private val router = HapRouter(store, television.accessory, setupCode = { _setupCode.value ?: "" }, listener = object : HapRouter.Listener {
        override fun onPaired() {
            Log.i(TAG, "paired")
            _setupCode.value = null
            advertise()
        }
        override fun onUnpaired() {
            Log.i(TAG, "last pairing removed")
            _setupCode.value = newSetupCode()
            advertise()
        }
        override fun onIdentify() { Log.i(TAG, "identify") }
    })
    private val server = HapServer(router)

    init {
        // inputs come and go with the installed apps: swap the database, bump c# so controllers refetch it
        television.onDatabaseChanged = { db ->
            router.accessory = db
            publishDatabase()
            if (_running.value) advertise()
        }
    }

    /** Bumps `c#` when the database differs from the one controllers last saw (also across restarts). */
    private fun publishDatabase() {
        val signature = television.accessory.services.joinToString(",") { "${it.type}:${it.iid}" }
        if (store.databaseSignature != signature) {
            store.databaseSignature = signature
            store.bumpConfigNumber()
        }
    }

    val status: StateFlow<Status> = combine(_running, store.pairings, _setupCode, _port) { running, pairings, code, port ->
        Status(
            running = running, paired = pairings.isNotEmpty(), controllers = pairings.size,
            setupCode = if (running && pairings.isEmpty()) code else null,
            setupUri = if (running && pairings.isEmpty() && code != null) SetupPayload.uri(code, store.setupId, TelevisionAccessory.CATEGORY_TELEVISION) else null,
            accessoryId = store.accessoryId, port = port,
        )
    }.stateIn(scope, SharingStarted.Eagerly, Status(accessoryId = store.accessoryId))

    @Synchronized
    fun start() {
        if (_running.value) return
        _port.value = server.start()
        publishDatabase()
        if (!store.isPaired) _setupCode.value = newSetupCode()
        _running.value = true
        advertise()
        Log.i(TAG, "started on port ${_port.value}, id ${store.accessoryId}, paired=${store.isPaired}")
    }

    @Synchronized
    fun stop() {
        if (!_running.value) return
        advertiser.stop()
        router.closeAll()
        server.stop()
        _running.value = false
        _setupCode.value = null
        _port.value = 0
    }

    /** After a network change: re-register the record and drop controller sessions bound to the old addresses. */
    fun reannounce() {
        if (!_running.value) return
        router.closeAll()
        advertiser.reannounce()
    }

    /** Renames the accessory in Home and in the mDNS record. */
    fun setName(newName: String) {
        if (newName == name) return
        name = newName
        television.setName(newName)
        store.bumpConfigNumber()
        if (_running.value) advertise()
    }

    /** Forgets every controller; the TV must be added to Home again. */
    fun resetPairings() {
        store.removeAll()
        router.closeAll()
        if (_running.value) {
            _setupCode.value = newSetupCode()
            advertise()
        }
    }

    // Android's mDNS stack requires instance names to be unique across service types, so the
    // HAP record cannot share the AirPlay name; the accessory itself still carries the plain name
    private fun advertise() = advertiser.advertise(
        HapAdvertiser.Record(
            name = "$name HomeKit", port = _port.value, accessoryId = store.accessoryId, model = info.model,
            configNumber = store.configNumber, paired = store.isPaired, category = TelevisionAccessory.CATEGORY_TELEVISION,
            setupHash = SetupPayload.setupHash(store.setupId, store.accessoryId),
        ),
    )

    companion object {
        private const val TAG = "HomeKit"

        // Apple rejects trivial codes (HAP 4.2.1.2)
        private val FORBIDDEN = setOf("00000000", "11111111", "22222222", "33333333", "44444444", "55555555", "66666666",
            "77777777", "88888888", "99999999", "12345678", "87654321")

        /** Random 8-digit setup code formatted `XXX-XX-XXX`. */
        fun newSetupCode(): String {
            val rnd = SecureRandom()
            var digits: String
            do { digits = (1..8).joinToString("") { rnd.nextInt(10).toString() } } while (digits in FORBIDDEN)
            return "${digits.substring(0, 3)}-${digits.substring(3, 5)}-${digits.substring(5)}"
        }
    }
}
