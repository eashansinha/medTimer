// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("androidx.room") version "2.8.5" apply false
    id("androidx.navigation.safeargs") version "2.10.2" apply false
}

buildscript {
    repositories {
        google()
        maven { url = uri("https://jitpack.io") }
    }
    dependencies {
        // AGP 9 uses built-in Kotlin; the Kotlin Gradle plugin only needs to be on the classpath.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}
