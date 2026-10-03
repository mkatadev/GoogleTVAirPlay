package pl.prodevcode.tvairplay.domain.model

enum class CheckResult { OK, WARNING, ERROR, UNKNOWN }

enum class AdvertisingState { IDLE, PENDING, REGISTERED, FAILED }

data class NetworkDiagnostics(
    val transport: String = "",
    val interfaceName: String = "",
    val addresses: List<String> = emptyList(),
    val changes: Int = 0,
)

data class AdvertisingDiagnostics(
    val raop: AdvertisingState = AdvertisingState.IDLE,
    val airplay: AdvertisingState = AdvertisingState.IDLE,
    val raopError: Int = 0,
    val airplayError: Int = 0,
    val announcements: Int = 0,
)

data class SessionDiagnostics(
    val connectedClients: Int = 0,
    val mode: SessionMode = SessionMode.IDLE,
    val videoCodec: String = "",
    val videoResolution: String = "",
    val videoFps: Int = 0,
    val droppedFrames: Long = 0,
    val audioCodec: String = "",
    val audioUnderruns: Int = 0,
)

/** Everything the "why can't my iPhone see the TV" screen needs, refreshed live. */
data class Diagnostics(
    val appVersion: String = "",
    val deviceName: String = "",
    val receiverStatus: ReceiverStatus = ReceiverStatus.STOPPED,
    val port: Int = 0,
    val lastError: String? = null,
    val network: NetworkDiagnostics = NetworkDiagnostics(),
    val advertising: AdvertisingDiagnostics = AdvertisingDiagnostics(),
    val session: SessionDiagnostics = SessionDiagnostics(),
    val lastLog: String = "",
)

/** One row of the checklist shown to the user. */
enum class DiagnosticCheck { RECEIVER, NETWORK, ADVERTISING, PORT }

/** Pure evaluation of the checklist so it can be unit-tested and reused by the notification later. */
object DiagnosticsEvaluator {
    fun evaluate(d: Diagnostics): Map<DiagnosticCheck, CheckResult> = mapOf(
        DiagnosticCheck.RECEIVER to when (d.receiverStatus) {
            ReceiverStatus.RUNNING -> CheckResult.OK
            ReceiverStatus.ERROR -> CheckResult.ERROR
            ReceiverStatus.STARTING -> CheckResult.UNKNOWN
            ReceiverStatus.STOPPED -> CheckResult.WARNING
        },
        DiagnosticCheck.NETWORK to when {
            d.network.addresses.isEmpty() -> CheckResult.ERROR
            d.network.addresses.any { it.startsWith("169.254.") } -> CheckResult.WARNING
            else -> CheckResult.OK
        },
        DiagnosticCheck.ADVERTISING to when {
            d.receiverStatus != ReceiverStatus.RUNNING -> CheckResult.UNKNOWN
            d.advertising.airplay == AdvertisingState.FAILED || d.advertising.raop == AdvertisingState.FAILED -> CheckResult.ERROR
            d.advertising.airplay == AdvertisingState.REGISTERED -> CheckResult.OK
            else -> CheckResult.UNKNOWN
        },
        DiagnosticCheck.PORT to when {
            d.receiverStatus == ReceiverStatus.ERROR && d.lastError?.contains("port", ignoreCase = true) == true -> CheckResult.ERROR
            d.receiverStatus == ReceiverStatus.RUNNING && d.port > 0 -> CheckResult.OK
            else -> CheckResult.UNKNOWN
        },
    )
}
