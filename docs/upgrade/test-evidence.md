# EE-31 — Test evidence (JVM unit tests and coverage, before/after)

**Status: DRAFT for human review.** Every number comes from a command run on this branch; the
command is quoted next to it. Baseline numbers are copied from `docs/upgrade/baseline.md`.

## 1. Commands

```
# full JVM suite, lint, and unit-test-only JaCoCo report (session-local init script, see baseline.md §3)
./gradlew --no-daemon testDebugUnitTest --rerun -I /tmp/gradle-init/jacoco-unit.gradle jacocoUnitReport lintDebug --continue
# results: app/build/test-results/testDebugUnitTest/*.xml
#          app/build/reports/jacoco/jacocoUnitReport/jacocoUnitReport.csv
#          app/build/reports/lint-results-debug.txt
```

The init script registers a `JacocoReport` over the `.exec` written by `testDebugUnitTest`
(`enableUnitTestCoverage = true`) with the repo's class exclusions. For AGP 9 the class
directories are `build/intermediates/javac/debug/compileDebugJavaWithJavac/classes` and
`build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes` (AGP 8.9 used
`intermediates/javac/debug` and `tmp/kotlin-classes/debug`).

## 2. Unit tests

| | Baseline (`main` @ 8fde0b91) | After (this branch) |
|---|---|---|
| Tests run | 56 | **79** (+23) |
| Failures / errors / skipped | 0 / 0 / 0 | 0 / 0 / 0 |
| Build | `BUILD SUCCESSFUL` | `BUILD SUCCESSFUL` |
| Lint (`lintDebug`) | **FAILED**, 19 errors (`GradleDependency`) | **4 errors** (`GradleDependency` for the 4 pinned Espresso artifacts, see §5); nothing suppressed |

New tests (all JUnit 5 + `RobolectricExtension`, `@Config(sdk = [35])`, in
`app/src/test/java/com/futsch1/medtimer/widgets/`):

| Class | Tests | What is checked |
|---|---|---|
| `WidgetLineProviderTest` | 14 | `NextRemindersLineProvider`: time + amount + name formatting, date prefix for other days, list order, empty state, lines beyond the list, 10-item list; `LatestRemindersLineProvider`: TAKEN / SKIPPED / RAISED status text, date prefix, order, empty state, lines beyond the list; `getDayString` |
| `WidgetImplTest` | 7 | large layout renders 4 provider lines; empty lines are `INVISIBLE`; empty state; long list truncated to `LARGE_WIDGET_LINES`=4 / `SMALL_WIDGET_LINES`=1 (provider asked only for lines 0–3 / 0); click on the widget root starts `MainActivity`; **IMP-01 guard**: the sized `RemoteViews` parcel is < 64 KiB and far below `1.5 × w × h × 4` bytes; `performWidgetUpdate` calls `updateAppWidget` once per id |
| `WidgetUpdateReceiverTest` | 2 | **IMP-07**: `WidgetUpdateReceiver.ACTION_WIDGET_UPDATE` resolves to exactly `WidgetUpdateReceiver` via `PackageManager.queryBroadcastReceivers` (manifest filter match); `onReceive` refreshes every placed next-/latest-reminders widget through the real providers and the (empty) Room database |

Each test was seen failing during development (wrong expectation, missing `--add-opens`,
ShadowAppWidgetManager reapply behaviour) before reaching its final form; none is tautological —
e.g. the IMP-01 guard fails if a `setImageViewBitmap` payload is added, the IMP-07 test fails if
the constant and the manifest diverge, and the truncation test fails if `WidgetImpl` asks the
provider for a fifth line.

## 3. Coverage (JaCoCo, unit tests only)

| Scope | Baseline lines | After lines | Baseline instr. | After instr. | After branch |
|---|---|---|---|---|---|
| Whole app (excl. R/BuildConfig/Args/Directions/tests) | 11.0 % (764 / 6970) | **16.4 % (1120 / 6810)** | 12.3 % | 17.0 % | 14.7 % (295 / 2002) |
| `com.futsch1.medtimer.widgets` | 2.0 % (3 / 147) | **96.2 % (153 / 159)** | 1.2 % (9 / 720) | 96.6 % (654 / 677) | 94.7 % (18 / 19) |

Per touched class (lines covered / total; "after" includes the small refactors):

| Class | Baseline | After |
|---|---|---|
| `WidgetImpl` | 0 / 42 | 45 / 46 |
| `WidgetIds` | 0 / 4 | 4 / 4 |
| `WidgetImplKt` (`performWidgetUpdate`) | 0 / 3 | 3 / 3 |
| `NextRemindersLineProvider` (+ lambdas) | 0 / 13 (+0/7, 0/3, 0/2) | 17 / 17 (+2/2, 2/2) |
| `LatestRemindersLineProvider` (+ lambdas) | 0 / 18 (+0/4, 0/2) | 22 / 22 (+2/2, 2/2) |
| `WidgetLineProviderKt` (`getDayString`, repository loaders) | 0 / 6 | 13 / 13 (+ `TimeAccess` anon. 1 / 3) |
| `NextRemindersWidgetProvider` / `…ProviderKt` | 1 / 6, 0 / 8 | 5 / 6, 8 / 8 |
| `LatestRemindersWidgetProvider` / `…ProviderKt` | 1 / 6, 0 / 8 | 4 / 6, 8 / 8 |
| `WidgetUpdateReceiver` | 1 / 15 | 15 / 15 |

Remaining uncovered widget lines: `onEnabled`/`onDisabled` no-op bodies of the two
`AppWidgetProvider`s, one `TimeAccess` lambda line (`LocalDate.now()` is only reached when the
scheduler has medicines), and one `WidgetImpl` line (pre-API-31 `else` branch of the `SizeF` map,
unreachable with `@Config(sdk = [35])`). The whole-app total also moved because other classes were
exercised indirectly by the receiver test (Room/`MedicineRepository`).

## 4. Robolectric SDK level — why the new tests run on SDK 35, not 37 (IMP-T9)

Robolectric 4.17 refuses to create an SDK 36 or 37 sandbox on Java 17
(`Failed to create a Robolectric sandbox: Android SDK 37 requires Java 21 (have Java 17)`,
same message for SDK 36). This was tried:

```
# app/build.gradle.kts (experiment, reverted)
tasks.withType(Test::class) {
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")   // needed by ApplicationSharedMemory on SDK 36+
}
./gradlew --no-daemon testDebugUnitTest --rerun -I /tmp/gradle-init/jacoco-unit.gradle jacocoUnitReport lintDebug --continue
```

Result with the test JVM on JDK 21 and the widget tests at `@Config(sdk = [37])`:
77 tests, **2 failed — both pre-existing tests, unrelated to the widgets**:

| Test | Failure on JDK 21 | Cause |
|---|---|---|
| `CSVExportUnitTest.test_create_csv_file_with_correct_headers_and_data` | expected `5/3/21 1:00 AM;…` but `java.text.DateFormat` on JDK 20+ (CLDR 42) emits a narrow no-break space (U+202F) before `AM` | hard-coded JDK-17 time format in the test's expected string |
| `SnoozeWorkUnitTest.testDoWorkSnooze` | `NullPointerException: "timestamp" is null` at `RescheduleWork.enqueueNotification` (`RescheduleWork.java:97`) | the test's `mockStatic(Instant.class)` stubs stop matching on JDK 21 |

All 21 widget tests that existed at that point passed in the SDK 37 sandbox. Because the project,
its CI (`setup-java` 17) and the working agreement are on JDK 17, and fixing the two tests is out
of this ticket's scope, the branch keeps the JDK 17 test JVM and runs the widget tests on SDK 35
(the highest Robolectric SDK available on Java 17). API 36/37 runtime behaviour is therefore
covered by the API 37 instrumented run in CI and by the Android 17 analysis in
`docs/upgrade-impact-android-17-widgets.md`, not by the JVM suite. **Open item**: move the test
JVM to JDK 21 (and adapt the two tests above) to run the Robolectric suite on SDK 37.

## 5. Instrumented tests, Espresso pin and the remaining lint findings

```
ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon connectedDebugAndroidTest --continue   # API 35
```

| Build | Instrumented tests (API 35) | Result |
|---|---|---|
| branch with Espresso 3.7.0 | 21 | 15 failed — all `LooperIdlingResource-NN-Export` idle timeouts |
| `main` (Espresso 3.6.1), `BasicUITest` + `CalendarTest` only | 3 | 0 failed |
| branch, only Espresso reverted to 3.6.1, `BasicUITest` | 2 | 0 failed |
| **branch final (Espresso pinned 3.6.1)** | **21** | **0 failed** (16 min 37 s) |

Espresso 3.7.0 is therefore pinned back to 3.6.1 (`app/build.gradle.kts`, with a comment). The
consequence for lint:

```
./gradlew --no-daemon lintDebug
# Lint found 4 errors: GradleDependency for espresso-core, espresso-contrib,
# espresso-idling-resource, idling-concurrent (3.6.1 -> 3.7.0 available)
```

Baseline 19 → **4** `GradleDependency` errors, nothing suppressed, no `lint.xml`/baseline change.
The 4 findings are the price of keeping the instrumented suite green; resolving them means fixing
the app's debug-only `IdlingRegistry.registerLooperAsIdlingResource` usage for Espresso 3.7
(open item). Full emulator evidence: `docs/upgrade/evidence/README.md`.
