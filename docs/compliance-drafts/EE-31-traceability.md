# EE-31 — Traceability matrix (DRAFT for human review)

Purpose: trace each requirement of Jira EE-31 ("Modernize the home-screen widgets module for
Android 17 (API 37)") to the analysis item, the change (commit), the test and the evidence that
verifies it. This is a draft input for the project's own review process; it does **not** assert
conformity with IEC 62304, FDA guidance or any other standard.

Branch `devin/ee-31-widgets-android-17` on `eashansinha/medTimer`, base `main` @ `8fde0b91`.

| Req. | Requirement (from EE-31 / plan) | Analysis | Change (commit) | Test / check | Evidence |
|---|---|---|---|---|---|
| R1 | Record a baseline on `main` before any change | — | `d1b91ef5` (`docs/upgrade/baseline.md`) | `assembleDebug testDebugUnitTest lintDebug`, JaCoCo init script | baseline.md §1–3: 56 tests, 11.0 % / 2.0 % lines, lint 19 errors |
| R2 | Assess Android 16/17 behaviour changes for the widget module before code changes | IMP-01 … IMP-14, IMP-T1 … T8 | `683c559c` (`docs/upgrade-impact-android-17-widgets.md`) | review | impact doc tables §1–3, rollback §5 |
| R3 | App compiles with `compileSdk 37` / `targetSdk 37` | IMP-T1 … T7 | `14eb684b`, `2ef00e46`, `ecdd3cad`, `9f654adc` | `./gradlew assembleDebug` after each commit; `aapt2 dump badging` → `compileSdkVersion='37'`, `targetSdkVersion:'37'` | test-evidence.md §2, evidence/README.md |
| R4 | RemoteViews memory limit (Android 17) does not crash the widgets | IMP-01 (runtime-check) | `9e6c52ab` (KDoc + refactor) | `WidgetImplTest.sizedRemoteViewsParcelStaysFarBelowAndroid17BitmapLimit` | test-evidence.md §2; API 35 widget screenshots (`evidence/api35-05`, `-07`) |
| R5 | App-widget sizing / update behaviour unchanged on 16/17 | IMP-02, IMP-03, IMP-14 (N / RC) | none required | `WidgetImplTest` (size map, 4/1 lines), emulator placement | `evidence/api35-04-widget-picker.png`, `-05`, `-07` |
| R6 | `PendingIntent` flags / background-activity-launch hardening | IMP-04, IMP-05 (N) | none required | `WidgetImplTest.widgetRootOpensMainActivityOnClick` | `evidence/api35-08-widget-tap-opens-app.mp4` |
| R7 | Widget refresh path works and is intent-filter-consistent | IMP-06, IMP-07 | `7b87fbcf` (`ACTION_WIDGET_UPDATE`) | `WidgetUpdateReceiverTest` (2 tests) | `adb shell am broadcast … WIDGET_UPDATE` → `AppWidgetServiceImpl: … widget id: 2 / 5` (evidence/README.md) |
| R8 | JVM tests for line providers: formatting, ordering, empty state, truncation | — | `26af922c`, `9e6c52ab`, `cd1de20a` | `WidgetLineProviderTest` (14), `WidgetImplTest` (7), `WidgetUpdateReceiverTest` (2) | test-evidence.md §2–3 (79 tests, widgets 96.2 % lines) |
| R9 | Instrumented tests on API 35, `adb install` on API 37 | — | — | `connectedDebugAndroidTest` (API 35), `adb install` (API 37) | evidence/README.md, `EE-31-verification-report.md` |
| R10 | CI runs on API 37 | IMP-T8 | `793d6a86` (`.github/workflows/test.yml`) | GitHub Actions run (URL in verification report) | verification report §4 |
| R11 | Pre-existing problems recorded, not hidden; no lint suppression, no disabled tests | — | `2ef00e46` (versions), lint config untouched | `lintDebug` 0 errors without `disable`/`baseline` changes; 56 original tests still run | baseline.md §1, test-evidence.md §2, §4 |
| R12 | Draft documentation for human review | — | docs commits | review | `docs/architecture-widgets.md`, `docs/compliance-drafts/EE-31-*.md`, `CHANGELOG.md`, `README.md` |
| R13 | Rollback possible | impact doc §5 | per-commit structure | `git revert` of individual commits | impact doc §5 |

## Test → requirement index

| Test class / method | Req. |
|---|---|
| `WidgetLineProviderTest.*` (formatting, date prefix, order, empty, beyond list, long list, status text) | R8 |
| `WidgetImplTest.largeWidgetRendersFourLinesWithProviderText`, `emptyLinesAreInvisibleButKeepTheirSlot`, `emptyStateRendersNoVisibleLine`, `longListIsTruncatedToTheWidgetLineLimit` | R5, R8 |
| `WidgetImplTest.widgetRootOpensMainActivityOnClick` | R6 |
| `WidgetImplTest.sizedRemoteViewsParcelStaysFarBelowAndroid17BitmapLimit` | R4 |
| `WidgetImplTest.performWidgetUpdatePushesOneRemoteViewsPerWidgetId` | R7 |
| `WidgetUpdateReceiverTest.refreshActionMatchesTheManifestIntentFilter`, `onReceiveUpdatesEveryPlacedWidgetOfBothProviders` | R7 |
| Existing 56 unit tests (unchanged) | R11 |
| `connectedDebugAndroidTest` (API 35) | R9 |
