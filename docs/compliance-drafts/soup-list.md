# SOUP / third-party component list - Android 17 upgrade (EE-29)

**DRAFT for human review.** Engineering input only; this list does not claim compliance with IEC 62304, FDA guidance or
any other standard. Versions are taken from `build.gradle.kts`, `app/build.gradle.kts` and `gradle/wrapper/gradle-wrapper.properties`
on `main` (before) and on branch `devin/ee-29-android-17-upgrade` (after). "Scope" states whether the component ships in the
app (runtime) or is build/test only.

## Build toolchain

| Component | Before | After | Scope | Reason for change |
|---|---|---|---|---|
| Gradle (wrapper) | 8.11.1 | 9.8.1 | build | AGP 9.4 requires >= 9.6.0; lint reported 9.8.1 as newest |
| Android Gradle Plugin `com.android.application` | 8.9.0 | 9.4.1 | build | newest stable AGP supporting compileSdk 37 |
| Kotlin Gradle plugin `org.jetbrains.kotlin.android` | 2.1.10 | removed (AGP built-in Kotlin) | build | AGP 9 ships Kotlin support |
| Android SDK platform / build tools | 35 / 35.0.0 | 37.0 / 37.0.0 | build | compileSdk 37 |
| `androidx.room` Gradle plugin | 2.6.1 | 2.8.5 | build | matches Room runtime |
| `androidx.navigation.safeargs` | 2.8.9 | 2.10.2 | build | 2.8.x does not apply under AGP 9 |
| `com.github.triplet.play` (Gradle Play Publisher) | 3.12.1 | 4.1.1 | build (publishing, not used in CI tests) | 3.x does not apply under AGP 9 |
| `org.sonarqube` | 5.1.0.4882 | 5.1.0.4882 (unchanged) | build (analysis) | 7.5.0.8588 fails under AGP 9 ("No Android variant found") |
| `tech.apter.junit5.jupiter.robolectric-extension-gradle-plugin` | 0.9.0 | 0.9.0 | test | works with AGP 9 |
| `jacoco` Gradle plugin | bundled | bundled | test | class directories updated for built-in Kotlin |
| JDK | 17 | 17 | build | unchanged |

## Runtime dependencies (shipped in the APK)

| Component | Before | After |
|---|---|---|
| androidx.appcompat:appcompat | 1.7.0 | 1.8.0 |
| com.google.android.material:material | 1.12.0 | 1.14.0 |
| androidx.constraintlayout:constraintlayout | 2.2.1 | 2.2.2 |
| androidx.navigation:navigation-fragment-ktx / navigation-ui-ktx | 2.8.9 | 2.10.2 |
| androidx.preference:preference-ktx | 1.2.1 | 1.2.1 |
| androidx.lifecycle:lifecycle-extensions | 2.2.0 | 2.2.0 |
| androidx.lifecycle:lifecycle-viewmodel-ktx | 2.8.7 | 2.11.0 |
| androidx.work:work-runtime | 2.10.0 | 2.12.0 |
| androidx.core:core-ktx | 1.15.0 | 1.19.1 |
| androidx.room:room-runtime / room-compiler (annotationProcessor) | 2.6.1 | 2.8.5 |
| androidx.biometric:biometric | 1.1.0 | 1.1.0 |
| com.google.code.gson:gson | 2.12.1 | 2.12.1 |
| com.github.skydoves:colorpickerview | 3.1.0 | 3.1.0 |
| com.github.wwdablu:SimplyPDF | 2.1.1 | 2.1.1 |
| com.github.evrencoskun:TableView | 0.8.9.4 | 0.8.9.4 |
| com.androidplot:androidplot-core | 1.5.11 | 1.5.11 |
| com.github.AppIntro:AppIntro | 6.3.1 | 6.3.1 |
| com.kizitonwose.calendar:view | 2.6.2 | 2.6.2 |
| com.maltaisn:icondialog / iconpack | 3.3.0 | 3.3.0 |
| com.google.android.flexbox:flexbox | 3.0.0 | 3.0.0 |
| com.android.tools:desugar_jdk_libs | 2.1.5 | 2.1.5 |

## Test-only dependencies

| Component | Before | After |
|---|---|---|
| org.junit.jupiter (JUnit 5) | 5.12.0 | 5.12.0 |
| org.mockito:mockito-core / mockito-inline | 5.16.0 / 5.2.0 | 5.16.0 / 5.2.0 |
| org.robolectric:robolectric | 4.14.1 | 4.14.1 (tests pinned to `@Config(sdk = 34)`) |
| com.code-intelligence:jazzer-junit | 0.24.0 | 0.24.0 |
| androidx.work:work-testing | - | 2.12.0 (new, `testImplementation`, for `AutostartUnitTest`) |
| androidx.test.ext:junit | 1.2.1 | 1.3.0 |
| androidx.test.espresso:* | 3.6.1 | 3.7.0 |
| androidx.test:rules / runner | 1.6.1 / 1.6.2 | 1.7.0 / 1.7.0 |
| androidx.test:orchestrator | 1.5.1 | 1.6.1 |
| androidx.test.uiautomator:uiautomator | 2.3.0 | 2.4.0 |
| tools.fastlane:screengrab | 2.1.1 | 2.1.1 |
| com.adevinta.android:barista | 4.3.0 | 4.3.0 |

## Notes for the reviewer

* Exact artifact coordinates and the full transitive tree can be regenerated with `./gradlew :app:dependencies --configuration debugRuntimeClasspath`.
* Material 1.14.0 removed `colorPrimary` from its own R class; four call sites now use `androidx.appcompat.R.attr.colorPrimary` (same theme attribute).
* Room 2.8.5 changed its generated code (`EntityInsertAdapter`, `RoomOpenDelegate`), which is why the JaCoCo class count differs between baseline and final report.
