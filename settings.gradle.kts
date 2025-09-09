// Enable Gradle's configuration cache and parallel execution
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

// Configure build cache
buildCache {
    local {
        // Use local build cache
        directory = File(rootDir, "build-cache")
        // Enable the build cache for this build
        isEnabled = true
    }
}


pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
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

rootProject.name = "Gamified"
include(":app")