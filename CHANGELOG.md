# Changelog

DRAFT for human review - maintained from the Android 17 upgrade onwards; earlier releases are described in
`fastlane/metadata/android/en-US/changelogs/`.

## Unreleased - Android 17 (API 37) upgrade ([EE-29](https://cog-gtm.atlassian.net/browse/EE-29))

### Changed
- `compileSdk`/`targetSdk` 35 -> 37 (Android 15 -> Android 17); `minSdk` stays 28.
- Toolchain: Gradle 8.11.1 -> 9.8.1, Android Gradle Plugin 8.9.0 -> 9.4.1 with built-in Kotlin
  (the `org.jetbrains.kotlin.android` plugin is removed, `kotlinOptions` -> `kotlin { compilerOptions }`),
  build tools 37.0.0, Gradle Play Publisher 3.12.1 -> 4.1.1, Navigation Safe Args 2.8.9 -> 2.10.2.
- Dependencies: Room 2.6.1 -> 2.8.5, Navigation 2.8.9 -> 2.10.2, WorkManager 2.10.0 -> 2.12.0, core-ktx 1.15.0 -> 1.19.1,
  AppCompat 1.7.0 -> 1.8.0, Material 1.12.0 -> 1.14.0, ConstraintLayout 2.2.1 -> 2.2.2, Lifecycle 2.8.7 -> 2.11.0,
  AndroidX test libraries (junit 1.3.0, espresso 3.7.0, rules/runner 1.7.0, orchestrator 1.6.1, uiautomator 2.4.0).
- Edge-to-edge (IMP-01): the main screen pads itself for the system bars and display cutout via
  `ViewCompat.setOnApplyWindowInsetsListener` instead of `fitsSystemWindows`, because Android 16+ ignores the edge-to-edge opt-out.
- "Override do not disturb" (IMP-39/IMP-50): the ringer is only muted again / switched to vibrate after a reminder if the
  unmute actually took effect; Android 17 may silently ignore volume changes from background work.
- Coverage: the JaCoCo tasks read Kotlin classes from the built-in Kotlin output directory; new `jacocoUnitTestReport` task.
- CI: the instrumented suite runs on an Android 17 (API 37) emulator and uploads a launch screen recording.

### Added
- JVM tests: `RescheduleWorkUnitTest` (exact/inexact alarm selection, due reminders), `AutostartUnitTest`
  (boot/package-replaced restore), `NotificationSoundManagerUnitTest` (ringer restore).
- Documentation: `docs/upgrade-impact-android-17.md`, `docs/upgrade/baseline.md`, `docs/upgrade/test-evidence.md`,
  `docs/architecture.md`, `docs/compliance-drafts/`.

### Known limitations / open items
- `org.sonarqube` stays at 5.1.0.4882: 7.5.0.8588 does not apply under AGP 9 ("No Android variant found for project app"),
  so one lint `NewerVersionAvailable` error remains (19 lint errors on the Android 15 baseline).
- The Android 17 "override do not disturb" audio behaviour could not be verified on a device (API 37 emulator has no working UI on the development VM).
