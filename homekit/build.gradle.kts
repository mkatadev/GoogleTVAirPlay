plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "pl.prodevcode.homekit"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // android.util.Log is a no-op in JVM tests
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    // Ed25519 / X25519 for HAP pairing; ChaCha20-Poly1305 comes from the platform JCA provider
    implementation(libs.tink.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
    testImplementation(project(":testing"))
}
