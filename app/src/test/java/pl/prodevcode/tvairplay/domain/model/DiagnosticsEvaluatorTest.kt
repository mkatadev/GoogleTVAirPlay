package pl.prodevcode.tvairplay.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticsEvaluatorTest {

    private val healthy = Diagnostics(
        receiverStatus = ReceiverStatus.RUNNING,
        port = 7000,
        network = NetworkDiagnostics(transport = "Wi-Fi", addresses = listOf("192.168.1.20")),
        advertising = AdvertisingDiagnostics(raop = AdvertisingState.REGISTERED, airplay = AdvertisingState.REGISTERED),
    )

    @Test fun `healthy receiver passes every check`() {
        val r = DiagnosticsEvaluator.evaluate(healthy)
        DiagnosticCheck.entries.forEach { assertEquals(it.name, CheckResult.OK, r[it]) }
    }

    @Test fun `no address is a network error`() {
        val r = DiagnosticsEvaluator.evaluate(healthy.copy(network = NetworkDiagnostics()))
        assertEquals(CheckResult.ERROR, r[DiagnosticCheck.NETWORK])
    }

    @Test fun `link-local address is a warning`() {
        val r = DiagnosticsEvaluator.evaluate(healthy.copy(network = NetworkDiagnostics(addresses = listOf("169.254.3.4"))))
        assertEquals(CheckResult.WARNING, r[DiagnosticCheck.NETWORK])
    }

    @Test fun `failed mDNS registration is an error only while running`() {
        val failed = healthy.copy(advertising = AdvertisingDiagnostics(airplay = AdvertisingState.FAILED, airplayError = 3))
        assertEquals(CheckResult.ERROR, DiagnosticsEvaluator.evaluate(failed)[DiagnosticCheck.ADVERTISING])
        val stopped = failed.copy(receiverStatus = ReceiverStatus.STOPPED)
        assertEquals(CheckResult.UNKNOWN, DiagnosticsEvaluator.evaluate(stopped)[DiagnosticCheck.ADVERTISING])
    }

    @Test fun `port conflict is reported on the port check`() {
        val r = DiagnosticsEvaluator.evaluate(
            healthy.copy(receiverStatus = ReceiverStatus.ERROR, port = 0, lastError = "Failed to start on port 7000")
        )
        assertEquals(CheckResult.ERROR, r[DiagnosticCheck.PORT])
        assertEquals(CheckResult.ERROR, r[DiagnosticCheck.RECEIVER])
    }

    @Test fun `stopped receiver is a warning, not an error`() {
        val r = DiagnosticsEvaluator.evaluate(healthy.copy(receiverStatus = ReceiverStatus.STOPPED, port = 0))
        assertEquals(CheckResult.WARNING, r[DiagnosticCheck.RECEIVER])
        assertEquals(CheckResult.UNKNOWN, r[DiagnosticCheck.PORT])
    }
}
