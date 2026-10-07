package pl.prodevcode.homekit.accessory

/** `RemoteKey` values the Home app / Control Center remote sends to a Television service. */
enum class RemoteKey(val hap: Int) {
    REWIND(0), FAST_FORWARD(1), NEXT_TRACK(2), PREVIOUS_TRACK(3), ARROW_UP(4), ARROW_DOWN(5),
    ARROW_LEFT(6), ARROW_RIGHT(7), SELECT(8), BACK(9), EXIT(10), PLAY_PAUSE(11), INFORMATION(15);

    companion object {
        fun of(hap: Int) = entries.firstOrNull { it.hap == hap }
    }
}

/** What a controller may do to the receiver; implemented by the host and invoked on HAP threads. */
interface TelevisionControls {
    fun setActive(active: Boolean)
    fun setPlaying(playing: Boolean)
    fun remoteKey(key: RemoteKey)
    fun setVolume(percent: Int)
    fun volumeStep(up: Boolean)
    fun setMuted(muted: Boolean)
    /** Controller picked an input (`ActiveIdentifier`); [id] is a [TvInput.id]. */
    fun selectInput(id: Int)
    /** Controller renamed an input in Home. */
    fun renameInput(id: Int, name: String)
    /** Controller showed/hid an input in Home. */
    fun setInputVisible(id: Int, visible: Boolean)
}

/** `InputSourceType` values HomeKit knows; only the ones a TV box can offer. */
enum class InputType(val hap: Int) { OTHER(0), HOME_SCREEN(1), AIRPLAY(8), APPLICATION(10) }

/**
 * One selectable input. [id] is the `Identifier` the controller writes to `ActiveIdentifier`; it must
 * stay stable for the same input across restarts so Home automations keep working.
 */
data class TvInput(val id: Int, val name: String, val type: InputType, val visible: Boolean = true) {
    init { require(id in 1..MAX_ID) { "input id out of range: $id" } }
    companion object { const val MAX_ID = 199 }
}

data class AccessoryInfo(
    val name: String,
    val manufacturer: String,
    val model: String,
    val serialNumber: String,
    val firmwareRevision: String,
)

/**
 * HomeKit Television (category 31) with a linked Television Speaker and a dynamic list of inputs
 * ([setInputs]). The accessory pushes receiver state in with the `set*` methods; controller writes
 * go to [controls]. Rebuilding the input list produces a new [accessory] and fires [onDatabaseChanged]
 * so the host can bump the configuration number and re-advertise.
 */
class TelevisionAccessory(info: AccessoryInfo, private val controls: TelevisionControls, inputs: List<TvInput> = listOf(DEFAULT_INPUT)) {

    private val active = Characteristic(Type.ACTIVE, Format.UINT8, RW_EV, 0, 0, 1, onWrite = { controls.setActive(it == 1L) })
    private val configuredName = Characteristic(Type.CONFIGURED_NAME, Format.STRING, RW_EV, info.name)
    private val currentMediaState = Characteristic(Type.CURRENT_MEDIA_STATE, Format.UINT8, R_EV, MEDIA_STOP, 0, 5)
    private val targetMediaState = Characteristic(
        Type.TARGET_MEDIA_STATE, Format.UINT8, RW_EV, MEDIA_STOP, 0, 2,
        onWrite = { controls.setPlaying((it as Long) == MEDIA_PLAY) },
    )
    private val mute = Characteristic(Type.MUTE, Format.BOOL, RW_EV, false, onWrite = { controls.setMuted(it as Boolean) })
    private val volume = Characteristic(Type.VOLUME, Format.UINT8, RW_EV, 0, 0, 100, 1, onWrite = { controls.setVolume((it as Long).toInt()) })
    private val activeIdentifier = Characteristic(
        Type.ACTIVE_IDENTIFIER, Format.UINT32, RW_EV, inputs.first().id, 1, TvInput.MAX_ID,
        validValues = inputs.map { it.id }, onWrite = { controls.selectInput((it as Long).toInt()) },
    )

    private val core: List<Service>
    private var inputServices = LinkedHashMap<Int, InputService>()

    @Volatile var accessory: Accessory
        private set

    /** Fired after [setInputs] changed the set of services; the new [accessory] replaces the old one. */
    var onDatabaseChanged: ((Accessory) -> Unit)? = null

    val inputs: List<TvInput> get() = inputServices.values.map { it.input }

    init {
        val information = Service(
            Type.ACCESSORY_INFORMATION,
            listOf(
                Characteristic(Type.IDENTIFY, Format.BOOL, setOf(Perm.WRITE), null),
                Characteristic(Type.MANUFACTURER, Format.STRING, R, info.manufacturer),
                Characteristic(Type.MODEL, Format.STRING, R, info.model),
                Characteristic(Type.NAME, Format.STRING, R, info.name),
                Characteristic(Type.SERIAL_NUMBER, Format.STRING, R, info.serialNumber),
                Characteristic(Type.FIRMWARE_REVISION, Format.STRING, R, info.firmwareRevision),
            ),
        )
        val protocol = Service(Type.PROTOCOL_INFORMATION, listOf(Characteristic(Type.VERSION, Format.STRING, R, "1.1.0")))
        val speaker = Service(
            Type.TELEVISION_SPEAKER,
            listOf(
                mute,
                Characteristic(Type.ACTIVE, Format.UINT8, R_EV, 1, 0, 1),
                Characteristic(Type.VOLUME_CONTROL_TYPE, Format.UINT8, R_EV, VOLUME_ABSOLUTE, 0, 3),
                Characteristic(Type.VOLUME_SELECTOR, Format.UINT8, setOf(Perm.WRITE), null, 0, 1, onWrite = { controls.volumeStep(up = it == 0L) }),
                volume,
            ),
        )
        val television = Service(
            Type.TELEVISION,
            listOf(
                active,
                activeIdentifier,
                configuredName,
                Characteristic(Type.SLEEP_DISCOVERY_MODE, Format.UINT8, R_EV, 1, 0, 1),
                Characteristic(Type.REMOTE_KEY, Format.UINT8, setOf(Perm.WRITE), null, 0, 16, onWrite = { v -> RemoteKey.of((v as Long).toInt())?.let(controls::remoteKey) }),
                currentMediaState,
                targetMediaState,
                Characteristic(Type.NAME, Format.STRING, R, info.name),
            ),
            primary = true,
        ).link(speaker)
        core = listOf(information, protocol, television, speaker)
        accessory = build(inputs)
    }

    /**
     * Replaces the input list. Inputs already present are updated in place (name/visibility events);
     * if inputs were added or removed the accessory database is rebuilt and [onDatabaseChanged] fires.
     */
    @Synchronized
    fun setInputs(newInputs: List<TvInput>) {
        require(newInputs.isNotEmpty()) { "a Television needs at least one input" }
        require(newInputs.map { it.id }.toSet().size == newInputs.size) { "duplicate input ids" }
        val sameSet = newInputs.map { it.id }.toSet() == inputServices.keys
        if (sameSet) {
            newInputs.forEach { inputServices.getValue(it.id).update(it) }
            return
        }
        accessory = build(newInputs)
        if (activeIdentifier.value as Long !in newInputs.map { it.id.toLong() }) activeIdentifier.update(newInputs.first().id)
        onDatabaseChanged?.invoke(accessory)
    }

    fun setActiveInput(id: Int) {
        if (inputServices.containsKey(id)) activeIdentifier.update(id)
    }

    private fun build(newInputs: List<TvInput>): Accessory {
        val previous = inputServices
        inputServices = LinkedHashMap()
        newInputs.forEach { input ->
            val service = previous[input.id]?.also { it.update(input) } ?: InputService(input)
            inputServices[input.id] = service
        }
        val television = core.first { it.type == Type.TELEVISION }
        television.linked.removeAll { it.type == Type.INPUT_SOURCE }
        inputServices.values.forEach { television.link(it.service) }
        activeIdentifier.validValues = newInputs.map { it.id }
        return Accessory(core + inputServices.values.map { it.service })
    }

    /** `InputSource` service with fixed iids derived from the input id, so ids survive rebuilds. */
    private inner class InputService(input: TvInput) {
        var input: TvInput = input
            private set
        private val base = INPUT_IID_BASE + input.id * INPUT_IID_STRIDE
        private val configured = Characteristic(
            Type.CONFIGURED_NAME, Format.STRING, RW_EV, input.name, iid = base + 2,
            onWrite = { controls.renameInput(this.input.id, it as String) },
        )
        private val currentVisibility = Characteristic(Type.CURRENT_VISIBILITY_STATE, Format.UINT8, R_EV, visibility(input.visible), 0, 3, iid = base + 5)
        private val targetVisibility = Characteristic(
            Type.TARGET_VISIBILITY_STATE, Format.UINT8, RW_EV, visibility(input.visible), 0, 1, iid = base + 6,
            onWrite = { controls.setInputVisible(this.input.id, (it as Long) == VISIBLE) },
        )
        val service = Service(
            Type.INPUT_SOURCE,
            listOf(
                Characteristic(Type.IDENTIFIER, Format.UINT32, R, input.id, iid = base + 1),
                configured,
                Characteristic(Type.INPUT_SOURCE_TYPE, Format.UINT8, R_EV, input.type.hap, 0, 10, iid = base + 3),
                Characteristic(Type.IS_CONFIGURED, Format.UINT8, RW_EV, 1, 0, 1, iid = base + 4),
                currentVisibility,
                targetVisibility,
                Characteristic(Type.NAME, Format.STRING, R, input.name, iid = base + 7),
            ),
            iid = base,
        )

        fun update(newInput: TvInput) {
            input = newInput
            configured.update(newInput.name)
            currentVisibility.update(visibility(newInput.visible))
            targetVisibility.update(visibility(newInput.visible))
        }

        private fun visibility(visible: Boolean) = if (visible) VISIBLE else HIDDEN
    }

    fun setActive(isActive: Boolean) = active.update(if (isActive) 1 else 0)

    fun setName(name: String) = configuredName.update(name)

    /** `null` = nothing is playing or paused (stopped). */
    fun setPlaying(playing: Boolean?) {
        val state = when (playing) { true -> MEDIA_PLAY; false -> MEDIA_PAUSE; null -> MEDIA_STOP }
        currentMediaState.update(state)
        targetMediaState.update(state)
    }

    fun setVolume(percent: Int, muted: Boolean) {
        volume.update(percent.coerceIn(0, 100))
        mute.update(muted)
    }

    /** Apple-defined short UUIDs. */
    object Type {
        const val ACCESSORY_INFORMATION = "3E"
        const val PROTOCOL_INFORMATION = "A2"
        const val TELEVISION = "D8"
        const val INPUT_SOURCE = "D9"
        const val TELEVISION_SPEAKER = "113"

        const val IDENTIFY = "14"
        const val MANUFACTURER = "20"
        const val MODEL = "21"
        const val NAME = "23"
        const val SERIAL_NUMBER = "30"
        const val FIRMWARE_REVISION = "52"
        const val VERSION = "37"
        const val ACTIVE = "B0"
        const val ACTIVE_IDENTIFIER = "E7"
        const val CONFIGURED_NAME = "E3"
        const val SLEEP_DISCOVERY_MODE = "E8"
        const val REMOTE_KEY = "E1"
        const val CURRENT_MEDIA_STATE = "E0"
        const val TARGET_MEDIA_STATE = "137"
        const val IDENTIFIER = "E6"
        const val INPUT_SOURCE_TYPE = "DB"
        const val IS_CONFIGURED = "D6"
        const val CURRENT_VISIBILITY_STATE = "135"
        const val TARGET_VISIBILITY_STATE = "134"
        const val MUTE = "11A"
        const val VOLUME_CONTROL_TYPE = "E9"
        const val VOLUME_SELECTOR = "EA"
        const val VOLUME = "119"
    }

    companion object {
        const val CATEGORY_TELEVISION = 31

        private val R = setOf(Perm.READ)
        private val R_EV = setOf(Perm.READ, Perm.EVENTS)
        private val RW_EV = setOf(Perm.READ, Perm.WRITE, Perm.EVENTS)

        /** The receiver itself; always present so the accessory is valid before the host supplies inputs. */
        val DEFAULT_INPUT = TvInput(1, "AirPlay", InputType.AIRPLAY)

        // core services use sequential iids (1..); every input owns a fixed block above them
        private const val INPUT_IID_BASE = 100
        private const val INPUT_IID_STRIDE = 8
        private const val VISIBLE = 0L
        private const val HIDDEN = 1L
        private const val VOLUME_ABSOLUTE = 3
        private const val MEDIA_PLAY = 0L
        private const val MEDIA_PAUSE = 1L
        private const val MEDIA_STOP = 2L
    }
}
