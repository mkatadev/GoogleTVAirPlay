plugins {
    alias(libs.plugins.android.library)
}

// JVM test helpers shared by the library modules (test-only dependency, never shipped)
android {
    namespace = "pl.prodevcode.testing"
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
}
