# SOUP / third-party component list — MedTimer 1.17 (Android 17 upgrade)

**Status: DRAFT for human review.** Ticket [EE-32](https://cog-gtm.atlassian.net/browse/EE-32). This list enumerates software of unknown provenance / third-party components declared in `build.gradle.kts` and `app/build.gradle.kts` with their versions before (`main` @ 8fde0b91, v1.16.5) and after the upgrade branch. It is an input for a regulated team's SOUP register; it does **not** assert compliance with IEC 62304 or any other standard, and anomaly-list / known-issue review per component is an open item for the human reviewer.

## Build toolchain

| Component | Before | After | Source / notes |
|---|---|---|---|
| Gradle | 8.11.1 | 9.6.0 | services.gradle.org; AGP 9.4 minimum |
| Android Gradle Plugin (`com.android.application`) | 8.9.0 | 9.4.1 | Google Maven; max supported API 37 |
| Kotlin Gradle plugin (classpath only, built-in Kotlin) | 2.1.10 | 2.4.21 | Maven Central |
| `androidx.room` Gradle plugin | 2.6.1 | 2.8.5 | Google Maven |
| `androidx.navigation.safeargs` | 2.8.9 | 2.10.2 | Google Maven |
| `com.github.triplet.play` (Play publisher; not used in CI, no credentials) | 3.12.1 | 4.1.1 | Gradle plugin portal; required for AGP 9 DSL |
| `org.sonarqube` | 5.1.0.4882 | 5.1.0.4882 | unchanged (optional CI step) |
| `tech.apter.junit5.jupiter.robolectric-extension-gradle-plugin` | 0.9.0 | 0.9.0 | unchanged; works under AGP 9.4.1 |
| `jacoco` (Gradle plugin) | Gradle default | see test-evidence | |
| JDK | 17 | 17 | |
| compileSdk / targetSdk / minSdk | 35 / 35 / 28 | 37 / 37 / 28 | |

## Runtime (`implementation`) dependencies

| Component | Before | After | Purpose |
|---|---|---|---|
| androidx.appcompat:appcompat | 1.7.0 | 1.8.0 | AppCompat activity/theme |
| com.google.android.material:material | 1.12.0 | 1.14.0 | Material components |
| androidx.constraintlayout:constraintlayout | 2.2.1 | 2.2.2 | layouts |
| androidx.navigation:navigation-fragment-ktx / navigation-ui-ktx | 2.8.9 | 2.10.2 | navigation |
| androidx.preference:preference-ktx | 1.2.1 | 1.2.1 | settings |
| androidx.lifecycle:lifecycle-extensions | 2.2.0 | 2.2.0 | deprecated artifact, still resolves (open item: remove) |
| androidx.work:work-runtime | 2.10.0 | 2.12.0 | WorkManager (IMP-14/15) |
| androidx.core:core-ktx | 1.15.0 | 1.19.1 | core KTX (EdgeToEdge / WindowInsetsCompat, IMP-01) |
| androidx.lifecycle:lifecycle-viewmodel-ktx | 2.8.7 | 2.11.0 | ViewModel |
| androidx.room:room-runtime / room-compiler (annotationProcessor) | 2.6.1 | 2.8.5 | database |
| com.github.martin-stone:hsv-alpha-color-picker-android (JitPack) | 3.1.0 | 3.1.0 | color picker |
| com.github.wwdablu:SimplyPDF (JitPack) | 2.1.1 | 2.1.1 | PDF export |
| com.google.code.gson:gson | 2.12.1 | 2.12.1 | JSON backup (IMP-28) |
| com.github.evrencoskun:TableView (JitPack) | v0.8.9.4 | v0.8.9.4 | tables |
| com.androidplot:androidplot-core | 1.5.11 | 1.5.11 | charts |
| com.github.AppIntro:AppIntro (JitPack) | 6.3.1 | 6.3.1 | onboarding |
| com.kizitonwose.calendar:view | 2.6.2 | 2.6.2 | calendar |
| com.maltaisn:icondialog | 3.3.0 | 3.3.0 | icon picker |
| androidx.test.espresso:espresso-idling-resource, espresso.idling:idling-concurrent | 3.6.1 | 3.6.1 (3.7.0 tried: LooperIdlingResource never idle, tests hang) | idling resources shipped in app |
| com.google.android.flexbox:flexbox | 3.0.0 | 3.0.0 | layouts |
| androidx.biometric:biometric | 1.1.0 | 1.1.0 | app lock (IMP-54) |
| com.android.tools:desugar_jdk_libs (coreLibraryDesugaring) | 2.1.5 | 2.1.5 | java.time on minSdk 28 |

## Test-only dependencies

| Component | Before | After |
|---|---|---|
| org.junit.jupiter:junit-jupiter-api | 5.12.0 | 5.12.0 |
| org.mockito:mockito-core | 5.16.0 | 5.16.0 |
| org.mockito:mockito-inline | 5.2.0 | 5.2.0 |
| org.robolectric:robolectric | 4.14.1 | see test-evidence |
| com.code-intelligence:jazzer-junit | 0.24.0 | 0.24.0 |
| androidx.test.ext:junit | 1.2.1 | 1.3.0 |
| androidx.test.espresso:espresso-core / espresso-contrib | 3.6.1 | 3.6.1 (3.7.0 hangs the suite) |
| androidx.test:rules | 1.6.1 | 1.7.0 |
| androidx.test:runner | 1.6.2 | 1.7.0 |
| androidx.test:orchestrator | 1.5.1 | 1.6.1 |
| androidx.test.uiautomator:uiautomator | 2.3.0 | 2.4.0 |
| com.adevinta.android:barista | 4.3.0 | 4.3.0 |
| tools.fastlane:screengrab | 2.1.1 | 2.1.1 |

Native code: the debug APK contains no `.so` files before or after (`unzip -l app/build/outputs/apk/debug/MedTimer-debug.apk | grep -c '\.so$'` → 0), so no 16 KB page-size or native SOUP considerations apply (IMP-11/18/36).
