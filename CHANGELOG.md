# Changelog

All notable changes to this fork of MedTimer are documented here (release notes for the app
itself live in `fastlane/metadata/android/*/changelogs/`).

## [Unreleased] — EE-31: home-screen widgets ready for Android 17 (API 37)

### Changed
- `compileSdk` / `targetSdk` 35 → **37**; Gradle 8.11.1 → 9.8.1; Android Gradle Plugin 8.9.0 →
  9.4.1 (built-in Kotlin 2.4.20); Room 2.6.1 → 2.8.5; Robolectric 4.14.1 → 4.17; navigation
  2.10.2, work 2.12.0, core-ktx 1.19.1, appcompat 1.8.0, material 1.14.0, lifecycle 2.11.0,
  AndroidX test libraries (Espresso stays at 3.6.1 — 3.7.0 breaks 15/21 instrumented tests, see
  `docs/upgrade/test-evidence.md` §5), gson 2.13.2 (`GradleDependency` lint errors 19 → 4).
- Widget refresh broadcast now uses the action declared in the manifest
  (`WidgetUpdateReceiver.ACTION_WIDGET_UPDATE`); previously the sender used a different action
  string (IMP-07).
- `WidgetImpl` builds its `RemoteViews` through `buildLargeRemoteViews()` /
  `buildSmallRemoteViews()` / `buildRemoteViews()` (no behaviour change) and documents the Android
  17 RemoteViews Bitmap/Icon memory limit (IMP-01).
- `NextRemindersLineProvider` / `LatestRemindersLineProvider` accept an injectable data loader
  (default unchanged: `MedicineRepository`).
- Material 1.14 removed `com.google.android.material.R.attr.colorPrimary`; the four call sites use
  `androidx.appcompat.R.attr.colorPrimary` (same theme attribute).
- CI (`.github/workflows/test.yml`) runs the instrumented tests on an API 37 emulator (was 34).
- SonarQube Gradle plugin disabled on this branch (does not apply with AGP 9.4 in this project).

### Added
- JVM tests for the widgets module: `WidgetLineProviderTest`, `WidgetImplTest`,
  `WidgetUpdateReceiverTest` (23 tests; widgets package line coverage 2.0 % → 96.2 %).
- Documentation (DRAFT for human review): `docs/upgrade/baseline.md`,
  `docs/upgrade-impact-android-17-widgets.md`, `docs/upgrade/test-evidence.md`,
  `docs/upgrade/evidence/`, `docs/architecture-widgets.md`, `docs/compliance-drafts/EE-31-*.md`.
