# Changelog

All notable changes to this fork of MedTimer are documented here. Upstream release notes for 1.16.5 and earlier live in `fastlane/metadata/android/en-US/changelogs/`.

## [1.17.0] — unreleased (branch `devin/ee-32-android-17-upgrade`, ticket [EE-32](https://cog-gtm.atlassian.net/browse/EE-32))

### Platform
- Target Android 17: `compileSdk` and `targetSdk` 35 → **37**; `minSdk` stays **28** (Android 9).
- Toolchain: Gradle 8.11.1 → 9.8.1, Android Gradle Plugin 8.9.0 → 9.4.1 (built-in Kotlin), Kotlin 2.1.10 → 2.4.21, Room 2.6.1 → 2.8.5, Navigation 2.8.9 → 2.10.2, JaCoCo 0.8.15.
- Dependencies: AppCompat 1.8.0, Material 1.14.0, core-ktx 1.19.1, WorkManager 2.12.0, lifecycle-viewmodel-ktx 2.11.0, ConstraintLayout 2.2.2, AndroidX test libraries (junit 1.3.0, runner/rules 1.7.0, orchestrator 1.6.1, uiautomator 2.4.0). Espresso stays at 3.6.1: 3.7.0 keeps the debug-only `Export` looper idling resource busy and hangs every instrumented test.

### Changed
- Theme attribute `colorPrimary` is now resolved through `androidx.appcompat.R.attr.colorPrimary` (Material 1.14 no longer exports it from its own R class). No visual change intended.
- SonarQube Gradle plugin and the optional CI SonarQube step removed on this branch (plugin cannot configure under AGP 9); Play publisher plugin updated to 4.1.1 but still never executed.
- CI (`.github/workflows/test.yml`) runs the instrumented suite on an API 37 `google_apis` x86_64 emulator and uploads a screen recording of the app launch.

### Tests
- New JVM tests: `RescheduleWorkUnitTest` (exact vs. inexact alarm scheduling), `AutostartUnitTest` (boot / package-replaced reschedule), `WidgetImplTest` (RemoteViews construction). 56 → 65 unit tests.
- New instrumented test `Android17BehaviorTest` (edge-to-edge insets, system back navigation).
- Unit-test-only coverage task `./gradlew jacocoUnitTestReport`.

### Documentation
- `docs/upgrade-impact-android-17.md` (Android 16/17 behavior-change impact assessment, IMP-01…IMP-55), `docs/upgrade/baseline.md`, `docs/upgrade/test-evidence.md`, `docs/architecture.md`, `docs/compliance-drafts/*` (all DRAFT for human review).

### Known limitations / open items
- No Android 17 (API 37) runtime evidence yet: the local API 37 AVD is install-only (SurfaceFlinger crash loop under the headless emulator) and the GitHub Actions API 37.0 emulator runs on the PR did not execute the instrumented suite (device runner could not install the APK / system services unavailable). All device evidence is from API 35.
- `TagTest.tagHandling` (instrumented) fails on the headless API 35 emulator before and after the upgrade (pre-existing).
- `androidx.lifecycle:lifecycle-extensions:2.2.0` is deprecated and still referenced.
