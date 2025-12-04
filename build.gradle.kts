// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    // Define versions in a single place
    extra.apply {
        set("kotlin_version", "1.9.22")  // Stable Kotlin version
        set("hilt_version", "2.57.1")
        set("room_version", "2.6.1")
        set("lifecycle_version", "2.7.0")
    }
    
    repositories {
        // Try Google's Maven repository first
        google()
        // Then try Maven Central with retry policy
        mavenCentral {
            content {
                includeGroupByRegex("org\\.jetbrains.*")
                includeGroupByRegex("com\\.google\\.*")
            }
        }
        // Add JitPack as a fallback
        maven { url = uri("https://jitpack.io") }
    }
    
    dependencies {
        classpath("com.google.dagger:hilt-android-gradle-plugin:${rootProject.extra["hilt_version"]}")
        classpath("com.google.gms:google-services:4.4.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${rootProject.extra["kotlin_version"]}")
    }
}

plugins {
    // These plugins are applied in the app module
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.google.services) apply false
    id("com.google.dagger.hilt.android") version "2.57.1" apply false
}

// Configure all projects to use the same Kotlin and dependency versions
allprojects {
    repositories {
        // Try Google's Maven repository first
        google()
        // Then try Maven Central with retry policy
        mavenCentral()
        // Add JitPack repository for audio recording libraries
        maven { url = uri("https://jitpack.io") }
    }
    configurations.all {
        resolutionStrategy {
            // Force specific versions to avoid conflicts
            force(
                "org.jetbrains.kotlin:kotlin-stdlib:${rootProject.extra["kotlin_version"]}",
                "org.jetbrains.kotlin:kotlin-stdlib-common:${rootProject.extra["kotlin_version"]}",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk7:${rootProject.extra["kotlin_version"]}",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${rootProject.extra["kotlin_version"]}",
                "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3",
                "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3",
                "androidx.room:room-common:${rootProject.extra["room_version"]}",
                "androidx.room:room-ktx:${rootProject.extra["room_version"]}",
                "androidx.room:room-runtime:${rootProject.extra["room_version"]}",
                "androidx.lifecycle:lifecycle-common:${rootProject.extra["lifecycle_version"]}",
                "androidx.lifecycle:lifecycle-viewmodel-ktx:${rootProject.extra["lifecycle_version"]}",
                "androidx.lifecycle:lifecycle-livedata-ktx:${rootProject.extra["lifecycle_version"]}",
                "androidx.lifecycle:lifecycle-runtime-ktx:${rootProject.extra["lifecycle_version"]}"
            )
        }
    }
}
