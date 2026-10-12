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
}
