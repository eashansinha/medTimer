// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("androidx.room") version "2.8.5" apply false
    id("androidx.navigation.safeargs") version "2.10.2" apply false
    // AGP 9 built-in Kotlin: KGP is only put on the classpath here (never applied) to pin its version
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
}

buildscript {
    repositories {
        google()
        maven { url = uri("https://jitpack.io") }
    }
}
