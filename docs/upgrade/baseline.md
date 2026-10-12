# EE-31 — Baseline measurement on `main` (before any change)

**Status: DRAFT for human review.** Every number below comes from a command run on this
branch's starting commit; the exact command is quoted next to each number.

| Item | Value |
|---|---|
| Repository / commit | `eashansinha/medTimer` `main` @ `8fde0b913ffb88c3ac7a22e68f30d599b4d134c3` (upstream v1.16.5 merge, 2025-03-15) |
| App version | `versionCode 105`, `versionName "1.16.5"` (`app/build.gradle.kts`) |
| compileSdk / targetSdk / minSdk | 35 / 35 / 28 |
| Toolchain | Gradle 8.11.1 (wrapper), AGP 8.9.0, Kotlin 2.1.10, Room 2.6.1, Robolectric 4.14.1, JUnit Jupiter 5.12.0, JDK 17 (`openjdk 17.0.20.1`) |
| Build host | Ubuntu 22.04 VM, Android SDK at `$ANDROID_HOME` with `platforms/android-35`, `platforms/android-37.0`, `build-tools/35.0.0`, `build-tools/37.0.0`; Maven Central mirrored through `~/.gradle/init.d/central-mirror.gradle` |
| Date | 2026-10-12 |

## 1. Build, unit tests, lint (single invocation)

```
./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug --continue
```

| Task | Result |
|---|---|
| `:app:assembleDebug` | **OK** — `app/build/outputs/apk/debug/MedTimer-debug.apk`, 31,273,811 bytes |
| `:app:testDebugUnitTest` | **OK** (see §2 for counts) |
| `:app:lintDebug` | **FAILED** — `Lint found 19 errors, 0 warnings` → `BUILD FAILED`, exit code 1 |

### Pre-existing lint failure (recorded, not hidden)

All 19 errors are `[GradleDependency]` "A newer version of … is available" findings in
`app/build.gradle.kts` (lines 119–161). `lint { warningsAsErrors = true; abortOnError = true }`
turns them into a build failure. They exist on the untouched baseline and are **not** caused by this
ticket. The findings as reported by `app/build/reports/lint-results-debug.txt`:

| Line | Dependency | Baseline | Newer available |
|---|---|---|---|
| 119 | androidx.appcompat:appcompat | 1.7.0 | 1.8.0 |
| 120 | com.google.android.material:material | 1.12.0 | 1.14.0 |
| 121 | androidx.constraintlayout:constraintlayout | 2.2.1 | 2.2.2 |
| 122 | androidx.navigation:navigation-fragment-ktx | 2.8.9 | 2.10.2 |
| 123 | androidx.navigation:navigation-ui-ktx | 2.8.9 | 2.10.2 |
| 126 | androidx.work:work-runtime | 2.10.0 | 2.12.0 |
| 127 | androidx.core:core-ktx | 1.15.0 | 1.19.1 |
| 128 | androidx.lifecycle:lifecycle-viewmodel-ktx | 2.8.7 | 2.11.0 |
| 129 | androidx.room:room-runtime | 2.6.1 | 2.8.5 |
| 138 | androidx.test.espresso:espresso-idling-resource | 3.6.1 | 3.7.0 |
| 139 | androidx.test.espresso.idling:idling-concurrent | 3.6.1 | 3.7.0 |
| 151 | androidx.test.ext:junit | 1.2.1 | 1.3.0 |
| 152 | androidx.test.espresso:espresso-core | 3.6.1 | 3.7.0 |
| 153 | androidx.test.espresso:espresso-contrib | 3.6.1 | 3.7.0 |
| 154 | androidx.test:rules | 1.6.1 | 1.7.0 |
| 156 | androidx.test.uiautomator:uiautomator | 2.3.0 | 2.4.0 |
| 157 | androidx.test:runner | 1.6.2 | 1.7.0 |
| 159 | androidx.test:orchestrator | 1.5.1 | 1.6.1 |
| 161 | androidx.room:room-compiler | 2.6.1 | 2.8.5 |

No lint rule was suppressed to take this baseline.

## 2. JVM unit tests

```
./gradlew --no-daemon testDebugUnitTest --rerun
```

Result: `BUILD SUCCESSFUL`. Counts aggregated from
`app/build/test-results/testDebugUnitTest/*.xml` (18 result files):

| Total | Failures | Errors | Skipped |
|---|---|---|---|
| **56** | 0 | 0 | 0 |

Fuzz tests (`**/*FuzzTest.class`) are excluded unless `-Dfuzzing=true` (existing build logic);
they were not run for the baseline.

## 3. Unit-test coverage (JaCoCo)

The repo's `JacocoDebugCodeCoverage` task depends on `connectedDebugAndroidTest` (needs an
emulator), so the unit-test-only number was produced from the `.exec` file that
`testDebugUnitTest` already writes (`enableUnitTestCoverage = true`) with a **session-local Gradle
init script** (no repository change) that registers a `JacocoReport` task over
`app/build/outputs/unit_test_code_coverage/**/*.exec` with the same class exclusions as the repo task:

```
./gradlew --no-daemon -I /tmp/gradle-init/jacoco-unit.gradle jacocoUnitReport
# report: app/build/reports/jacoco/jacocoUnitReport/{jacocoUnitReport.csv,xml,html}
```

| Scope | Lines | Instructions | Branches |
|---|---|---|---|
| Whole app (`com.futsch1.medtimer.*`, excl. R/BuildConfig/Args/Directions/tests) | **11.0 %** (764 / 6970) | 12.3 % | 12.7 % |
| `com.futsch1.medtimer.widgets` package | **2.0 %** (3 / 147) | 1.2 % (9 / 720) | — |

Per class in `com.futsch1.medtimer.widgets` (lines covered / total):

| Class | Covered / total lines |
|---|---|
| `WidgetImpl` | 0 / 42 |
| `WidgetIds` | 0 / 4 |
| `WidgetImplKt` (`performWidgetUpdate`) | 0 / 3 |
| `NextRemindersLineProvider` (+ coroutine/TimeAccess lambdas) | 0 / 13 (+ 0 / 7, 0 / 3, 0 / 2) |
| `LatestRemindersLineProvider` (+ coroutine lambdas) | 0 / 18 (+ 0 / 4, 0 / 2) |
| `WidgetLineProviderKt` (`getDayString`) | 0 / 6 |
| `NextRemindersWidgetProvider` / `NextRemindersWidgetProviderKt` | 1 / 6, 0 / 8 |
| `LatestRemindersWidgetProvider` / `LatestRemindersWidgetProviderKt` | 1 / 6, 0 / 8 |
| `WidgetUpdateReceiver` | 1 / 15 |

The only covered lines are class-initialisation lines touched indirectly by other tests; **no
existing unit test exercises the widget module's logic**.

## 4. Instrumented tests / emulator

Not run for the baseline (the baseline is a JVM-only measurement). Emulator evidence for the
upgraded build is recorded separately in `docs/upgrade/test-evidence.md` and `docs/upgrade/evidence/`.
