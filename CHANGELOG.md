# Changelog

User-facing release notes live in `fastlane/metadata/android/*/changelogs/`. This file tracks engineering
changes per ticket.

## Unreleased

### EE-30 — Reminder scheduling & notification module ready for Android 17 (API 37)

- Build: `compileSdk`/`targetSdk` 35 → 37, Gradle 8.11.1 → 9.8.1, AGP 8.9.0 → 9.4.1 (built-in Kotlin, the
  separate `org.jetbrains.kotlin.android` plugin is no longer applied), Safe Args 2.10.2, Play Publisher 4.1.1.
  SonarQube plugin disabled on this branch (incompatible with the AGP 9 variant API, see `app/build.gradle.kts`).
- Dependencies: WorkManager 2.12.0, Room 2.8.5, AppCompat 1.8.0, Core KTX 1.19.1, Navigation 2.10.2,
  Material 1.14.0, Robolectric 4.17 and AndroidX test libraries (resolves the 19 `GradleDependency` lint errors).
- IMP-07 (Android 16/17 Safer Intents): `com.futsch1.medTimer.SNOOZE_ACTION` is now declared in the
  `ReminderProcessor` receiver intent filter. No other reminder behaviour change.
- Tests: 35 new JVM tests (DST / month-boundary / cyclic scheduling, snooze bounds, exact-alarm policy, repeat
  limits, boot-receiver rescheduling, notification-action intents + manifest filter). 56 → 91 tests.
- CI: instrumented tests run on an API 37 emulator.
- Docs (drafts): `docs/upgrade/`, `docs/upgrade-impact-android-17-reminders.md`, `docs/architecture-reminders.md`,
  `docs/compliance-drafts/`.
