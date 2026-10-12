# Release notes (draft) - MedTimer Android 17 (API 37) upgrade

**DRAFT for human review.** Not a published release note; no claim of compliance with IEC 62304, FDA guidance or any other standard.
Ticket: [EE-29](https://cog-gtm.atlassian.net/browse/EE-29). Branch `devin/ee-29-android-17-upgrade` → `main`.

## What changed

* The app now compiles against and targets **Android 17 (API 37)**; it still runs on Android 9 (API 28) and newer.
* Build toolchain: Gradle 9.8.1, Android Gradle Plugin 9.4.1 with built-in Kotlin, build tools 37.0.0.
* Libraries: Room 2.8.5, Navigation 2.10.2, WorkManager 2.12.0, core-ktx 1.19.1, AppCompat 1.8.0, Material 1.14.0, ConstraintLayout 2.2.2, Lifecycle 2.11.0, AndroidX test libraries (details in `soup-list.md`).
* Edge-to-edge: the main screen handles system-bar and display-cutout insets explicitly (Android 16+ no longer allows opting out).
* "Override do not disturb": after a reminder the ringer is only muted again / set to vibrate if it had actually been unmuted (Android 17 can silently block volume changes from the background).
* Coverage tooling fixed for the new Kotlin compiler output; new `jacocoUnitTestReport` Gradle task.
* CI runs the instrumented suite on an Android 17 emulator and uploads an app-launch screen recording.

## Why

Android 16 and 17 change platform behaviour for apps targeting API 36/37 (forced edge-to-edge, predictive back by default, stricter
background audio, RemoteViews limits, …). The change impact assessment (`docs/upgrade-impact-android-17.md`, IMP-01…IMP-51) identified
which changes affect a medication-reminder app and the two that required code changes (IMP-01, IMP-39/50).

## Risk

* Medium: library major updates (Room 2.8, Material 1.14, Navigation 2.10) - mitigated by the full unit and instrumented suites.
* Medium: Android 17 background-audio hardening may limit the optional DND-override sound - behaviour is now safe (no wrong ringer state) but the OS effect is untested on a device.
* Low: toolchain migration (built-in Kotlin, Gradle 9) - build, lint, coverage verified.

## Verification evidence

* 67 JVM tests pass (56 before); unit-test coverage 15.94 % lines / 15.45 % branches (10.96 % / 12.72 % before).
* Android 15 emulator: 21 instrumented tests, 20 pass, 1 pre-existing failure (`TagTest.tagHandling`); manual reminder flow recorded (`docs/upgrade/evidence/api35-demo.mp4`, screenshots).
* Android 17 emulator: install succeeds (`targetSdk=37`); UI evidence from the CI run (link in the PR).
* Details: `docs/upgrade/test-evidence.md`, `docs/compliance-drafts/verification-report.md`.

## Known limitations

* Lint still reports 5 "newer version available" errors (espresso 3.7.0 hangs the UI tests; sonarqube 7.5 is incompatible with AGP 9).
* Android 17 UI could not be rendered on the development VM's emulator (SurfaceFlinger crash); only CI provides Android 17 UI evidence.
* `TagTest.tagHandling` fails on the headless emulator before and after the upgrade.

## Open items

1. Review the CI run on API 37 (artifacts `LaunchRecording-api37.0`, `TestReports`) and re-run if the emulator image was unstable.
2. Device test of the DND-override sound on an Android 17 device (IMP-39/50).
3. Investigate `OptionsMenu` idling-resource registration so Espresso 3.7.x can be adopted.
4. Replace the deprecated `InputMethodManager.SHOW_IMPLICIT` usage.
5. Human review and approval of all documents in `docs/compliance-drafts/`.
