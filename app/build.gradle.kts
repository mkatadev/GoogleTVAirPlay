import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Properties
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

fun loadProps(name: String) = Properties().apply {
    rootProject.file(name).takeIf { it.exists() }?.inputStream()?.use(::load)
}
val keystoreProps = loadProps("keystore.properties")
val localProps = loadProps("local.properties")

android {
    namespace = "pl.prodevcode.tvairplay"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "pl.prodevcode.tvairplay"
        minSdk = 31
        targetSdk = 37
        // CI passes the git tag: ./gradlew assembleRelease -PversionName=1.2.0 -PversionCode=10200
        // Local builds default to a huge versionCode so a dev install always replaces a published release on the TV.
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 999_999_999
        versionName = (findProperty("versionName") as String?) ?: "dev"
    }

    // release.keystore + keystore.properties are local (gitignored); without them release falls back to debug signing
    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.media3.ui)
    implementation(libs.androidx.appcompat)
    // AirPlay receiver core (UxPlay, GPL-3.0) — native build, see airplay-core/README.md
    implementation(project(":airplay-core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.tv.material)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}


// ---- Sideload to Chromecast / Google TV ------------------------------------------------
// Usage: ./gradlew installChromecast [-Pchromecast=192.168.1.42[:port] | -Pchromecast=<adb serial>]
// Without -Pchromecast the network is scanned (connected adb devices, mDNS wireless debugging, port 5555 on the
// local /24 networks, `chromecast.ip` from local.properties) and you pick the target; Enter keeps the last one.
// Enable Developer options → USB / wireless debugging on the TV first (wireless needs a one-time `adb pair`).

val adbExecutable = providers.provider {
    val sdk = localProps.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME") ?: error("Android SDK not found")
    "$sdk/platform-tools/adb"
}

abstract class AdbBaseTask : DefaultTask() {
    @get:Input abstract val adb: Property<String>

    /** stdout+stderr of an adb call, or null when it fails to finish in time */
    protected fun adb(vararg args: String, timeoutSec: Long = 10): String? {
        val process = ProcessBuilder(listOf(adb.get()) + args).redirectErrorStream(true).start()
        val output = StringBuilder()
        val reader = Thread { output.append(process.inputStream.bufferedReader().readText()) }.apply { start() }
        if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        reader.join(1_000)
        return output.toString()
    }
}

abstract class AdbDeviceTask : AdbBaseTask() {
    /** file written by `selectChromecast`; first line = adb serial */
    @get:InputFile abstract val target: RegularFileProperty

    @get:Internal protected val device: String get() = target.get().asFile.readLines().first().trim()
}

abstract class SelectChromecastTask : AdbBaseTask() {
    @get:Input @get:Optional abstract val requested: Property<String>
    @get:Input @get:Optional abstract val hint: Property<String>
    @get:OutputFile abstract val selection: RegularFileProperty

    private class Device(val serial: String, val hardwareId: String, val label: String, val tv: Boolean)

    private val ipv4 = Regex("""\d+\.\d+\.\d+\.\d+""")
    private val ipv4Port = Regex("""\d+\.\d+\.\d+\.\d+:\d+""")

    @TaskAction
    fun select() {
        val out = selection.get().asFile
        val previous = out.takeIf { it.exists() }?.readLines().orEmpty()
        val forced = requested.orNull?.trim().orEmpty()
        val chosen = if (forced.isNotEmpty()) {
            val serial = normalize(forced)
            if (ipv4Port.matches(serial)) adb("connect", serial)
            describe(serial) ?: throw GradleException("$serial is not reachable over adb")
        } else {
            choose(discover(previous.getOrNull(0)), previous)
        }
        out.parentFile.mkdirs()
        out.writeText("${chosen.serial}\n${chosen.hardwareId}\n")
        logger.lifecycle("Target: ${chosen.label}")
    }

    private fun normalize(address: String) = if (ipv4.matches(address)) "$address:5555" else address

    private fun discover(previousSerial: String?): List<Device> {
        logger.lifecycle("Scanning for adb devices (USB, mDNS, port 5555) …")
        val addresses = linkedSetOf<String>()
        val mdns = Regex("""\t_adb[\w-]*\._tcp\.?\t(\d+\.\d+\.\d+\.\d+:\d+)""")
        adb("mdns", "services")?.lineSequence()?.forEach { line -> mdns.find(line)?.let { addresses += it.groupValues[1] } }
        listOfNotNull(previousSerial, hint.orNull).map(::normalize).filter(ipv4Port::matches).forEach { addresses += it }
        scanPort(5555).forEach { addresses += "$it:5555" }

        val known = devices()
        // stale wireless entries (port changed after the TV slept) just clutter `adb devices`
        known.filter { (serial, state) -> state == "offline" && ipv4Port.matches(serial) }.keys.forEach { adb("disconnect", it) }
        val pool = Executors.newFixedThreadPool(8)
        addresses.filter { known[it] != "device" }
            .map { address -> pool.submit { adb("connect", address, timeoutSec = 5) } }
            .forEach { it.get() }
        pool.shutdown()

        val states = devices()
        states.filterValues { it == "unauthorized" }.keys.forEach {
            logger.warn("$it is unauthorized — accept the USB/wireless debugging prompt on the device")
        }
        return states.filterValues { it == "device" }.keys
            .mapNotNull(::describe)
            .groupBy { it.hardwareId }.values
            .map { sameDevice -> sameDevice.minBy { transportRank(it.serial) } }
            .sortedWith(compareBy<Device> { !it.tv }.thenBy { it.label })
    }

    /** adb serial → state, from the tab-separated `adb devices` output (mDNS serials contain spaces) */
    private fun devices(): Map<String, String> =
        adb("devices")?.lineSequence()?.drop(1)
            ?.mapNotNull { line -> line.split('\t').takeIf { it.size >= 2 }?.let { it[0] to it[1].trim() } }
            ?.toMap().orEmpty()

    // USB is fastest for the ~60 MB push, then a plain ip:port, then the mDNS alias
    private fun transportRank(serial: String) = when {
        serial.startsWith("emulator-") -> 3
        ipv4Port.matches(serial) -> 1
        "._adb" in serial -> 2
        else -> 0
    }

    private fun describe(serial: String): Device? {
        val props = adb(
            "-s", serial, "shell",
            "getprop ro.serialno; getprop ro.product.manufacturer; getprop ro.product.model; " +
                "getprop ro.build.characteristics; settings get global device_name",
            timeoutSec = 8,
        )?.lines()?.map { it.trim() }
        if (props == null || props.size < 5 || props[0].startsWith("error:")) return null
        val (hw, manufacturer, model, characteristics, name) = props
        val transport = when (transportRank(serial)) {
            0 -> "USB"
            3 -> "emulator"
            else -> "Wi-Fi $serial"
        }
        val title = name.takeIf { it.isNotBlank() && it != "null" } ?: model
        return Device(serial, hw.ifBlank { serial }, "$title ($manufacturer $model) — $transport", "tv" in characteristics)
    }

    private fun scanPort(port: Int): List<String> {
        val prefixes = NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.isPointToPoint }
            .flatMap { it.interfaceAddresses.asSequence() }
            .mapNotNull { it.address as? Inet4Address }
            .filter { it.isSiteLocalAddress }
            .map { it.hostAddress.substringBeforeLast('.') }
            .toSet()
        val pool = Executors.newFixedThreadPool(64)
        val probes = prefixes.flatMap { prefix -> (1..254).map { "$prefix.$it" } }.map { host ->
            host to pool.submit(Callable {
                runCatching { Socket().use { it.connect(InetSocketAddress(host, port), 300) } }.isSuccess
            })
        }
        return probes.filter { it.second.get() }.map { it.first }.also { pool.shutdownNow() }
    }

    private fun choose(devices: List<Device>, previous: List<String>): Device {
        if (devices.isEmpty()) throw GradleException(
            "No adb device found. On the TV enable Developer options → USB or wireless debugging " +
                "(pair once with `adb pair <ip:port>`), or pass -Pchromecast=<ip[:port]>"
        )
        if (devices.size == 1) return devices[0]
        val default = devices.indexOfFirst { it.serial == previous.getOrNull(0) || it.hardwareId == previous.getOrNull(1) }
            .takeIf { it >= 0 } ?: 0
        logger.quiet("\nSelect a device:")
        devices.forEachIndexed { i, d -> logger.quiet("  ${i + 1}) ${d.label}${if (i == default) "   [Enter]" else ""}") }
        logger.quiet("Number [${default + 1}]: ")
        val answer = readLine(ANSWER_TIMEOUT_MS)
        if (answer == null) {
            logger.lifecycle("No answer — using ${devices[default].label}")
            return devices[default]
        }
        if (answer.isBlank()) return devices[default]
        val index = answer.trim().toIntOrNull()?.minus(1)
        return devices.getOrNull(index ?: -1) ?: throw GradleException("Invalid choice: $answer")
    }

    // Gradle's forwarded stdin never reports available() > 0, so read on a side thread and time out
    // (e.g. an IDE run without a usable stdin) to fall back to the default
    private fun readLine(timeoutMs: Long): String? {
        val result = CompletableFuture<String?>()
        Thread {
            val line = StringBuilder()
            while (true) {
                val c = runCatching { System.`in`.read() }.getOrDefault(-1)
                if (c < 0) { result.complete(null); break }
                if (c == '\n'.code) { result.complete(line.toString()); break }
                if (c != '\r'.code) line.append(c.toChar())
            }
        }.apply { isDaemon = true; start() }
        return try {
            result.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            null
        }
    }

    private companion object {
        const val ANSWER_TIMEOUT_MS = 60_000L
    }
}

// `adb install` transfers silently for minutes over Wi-Fi; `adb push` reports progress, but only on a TTY and via `\r`
// (Gradle shows Exec output per `\n` only) — so run it under a pty and re-log every 5 %.
abstract class AdbPushTask : AdbDeviceTask() {
    @get:InputFile abstract val apk: RegularFileProperty
    @get:Input abstract val remotePath: Property<String>

    @TaskAction
    fun push() {
        val file = apk.get().asFile
        logger.lifecycle("Pushing ${file.length() / 1_048_576} MB to $device …")
        val cmd = listOf(adb.get(), "-s", device, "push", file.absolutePath, remotePath.get())
        val process = ProcessBuilder(listOf("script", "-q", "/dev/null") + cmd)
            .redirectErrorStream(true)
            .apply { environment()["TERM"] = "xterm" }
            .start()
        val percent = Regex("""(\d{1,3})%""")
        val line = StringBuilder()
        var lastPct = -5
        process.inputStream.bufferedReader().use { reader ->
            while (true) {
                val c = reader.read()
                if (c < 0) break
                if (c != '\r'.code && c != '\n'.code) { line.append(c.toChar()); continue }
                val text = line.toString().replace(Regex("""\u001B\[[0-9;]*[A-Za-z]"""), "").trim()
                line.setLength(0)
                val pct = percent.find(text)?.groupValues?.get(1)?.toInt()
                when {
                    text.isEmpty() -> Unit
                    pct == null -> logger.lifecycle(text)
                    pct >= lastPct + 5 || pct == 100 -> { lastPct = pct; logger.lifecycle("  $pct %") }
                }
            }
        }
        if (process.waitFor() != 0) throw GradleException("adb push to $device failed")
    }
}

abstract class AdbShellTask : AdbDeviceTask() {
    @get:Input abstract val command: Property<String>
    @get:Input abstract val doneMessage: Property<String>

    @TaskAction
    fun run() {
        val output = adb("-s", device, "shell", command.get(), timeoutSec = 300)?.trim()
            ?: throw GradleException("adb shell timed out on $device")
        if (output.isNotEmpty()) logger.lifecycle(output)
        if (output.contains("INSTALL_FAILED_VERSION_DOWNGRADE")) {
            throw GradleException("A newer versionCode is installed on $device — run `adb -s $device uninstall pl.prodevcode.tvairplay` first")
        }
        if (output.contains("Failure") || output.contains("Error") || output.startsWith("adb: error")) {
            throw GradleException("adb shell failed on $device")
        }
        logger.lifecycle("${doneMessage.get()} ($device)")
    }
}

val chromecastTarget = layout.buildDirectory.file("chromecast/target.txt")
val releaseApk = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
val remoteApk = "/data/local/tmp/tvairplay.apk"

val selectChromecast = tasks.register<SelectChromecastTask>("selectChromecast") {
    group = "chromecast"
    description = "Scan for adb devices (USB / mDNS / port 5555) and pick the Chromecast / Google TV"
    adb.set(adbExecutable)
    requested.set(providers.gradleProperty("chromecast"))
    hint.set(providers.provider { localProps.getProperty("chromecast.ip") })
    selection.set(chromecastTarget)
    doNotTrackState("device discovery has to run every time")
}

// ask for the device before the (long) release build, not after it
tasks.named("preBuild") { mustRunAfter(selectChromecast) }

val pushChromecast = tasks.register<AdbPushTask>("pushChromecast") {
    group = "chromecast"
    description = "Push the release APK to the Chromecast / Google TV (with transfer progress)"
    dependsOn("assembleRelease")
    adb.set(adbExecutable)
    target.set(selectChromecast.flatMap { it.selection })
    apk.set(releaseApk)
    remotePath.set(remoteApk)
    doNotTrackState("always push to the currently selected device")
}

val installApkChromecast = tasks.register<AdbShellTask>("installApkChromecast") {
    group = "chromecast"
    description = "Install the pushed APK on the Chromecast / Google TV"
    dependsOn(pushChromecast)
    adb.set(adbExecutable)
    target.set(selectChromecast.flatMap { it.selection })
    command.set("pm install -r $remoteApk; status=\$?; rm -f $remoteApk; exit \$status")
    doneMessage.set("Installed")
    doNotTrackState("always install")
}

tasks.register<AdbShellTask>("installChromecast") {
    group = "chromecast"
    description = "Scan & pick a device, build release, install and launch on the Chromecast / Google TV"
    dependsOn(installApkChromecast)
    adb.set(adbExecutable)
    target.set(selectChromecast.flatMap { it.selection })
    command.set("am start -n pl.prodevcode.tvairplay/.presentation.MainActivity")
    doneMessage.set("Launched")
    doNotTrackState("always launch")
}
