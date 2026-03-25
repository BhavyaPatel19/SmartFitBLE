pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal() // REQUIRED for KSP
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "SmartFitBLE"

include(":app", ":shared")
