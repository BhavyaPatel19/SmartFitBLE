plugins {
    // Android plugins
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false

    // Kotlin (STABLE)
    kotlin("android") version "1.9.22" apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false

    // KSP (MATCHING Kotlin 1.9.22)
    id("com.google.devtools.ksp") version "1.9.22-1.0.16" apply false

    // Google Services
    id("com.google.gms.google-services") version "4.4.4" apply false
}
