# EE-31 — Change record (DRAFT for human review)

This record describes what was changed, why, and how it was verified, as an input for the
project's review. It makes no claim of conformity with IEC 62304, FDA guidance or any standard.

| Field | Value |
|---|---|
| Ticket | [EE-31](https://cog-gtm.atlassian.net/browse/EE-31) — Modernize the home-screen widgets module for Android 17 (API 37) |
| Repository / branch | `eashansinha/medTimer`, `devin/ee-31-widgets-android-17` → PR into `main` |
| Base | `main` @ `8fde0b913ffb88c3ac7a22e68f30d599b4d134c3` (v1.16.5, Android 15 / API 35) |
| Author | Devin (session https://devin-gtm.devinenterprise.com/sessions/d7e10d44a6d940f898e3c2e1117affbb), requested by Eashan Sinha |
| Date | 2026-10-12 |
| Classification (proposal) | Toolchain/SDK upgrade + one defect fix (IMP-07) + testability refactor; no user-visible feature change; no database schema change |

## 1. Reason for change

Google Play and the Android platform move the target API forward yearly; the fork is on
targetSdk 35. Android 17 (API 37) introduces behaviour changes that touch app widgets (strict
RemoteViews Bitmap/Icon memory limit, activity-launch hardening, large-screen rules) and the
build toolchain (AGP 8.x cannot compile against API 37). EE-31 asks for the widget module to be
assessed and made ready for targetSdk 37 with tests and evidence.

## 2. Scope

In scope: `app/src/main/java/com/futsch1/medtimer/widgets/**`, widget layouts/metadata,
`RescheduleWork.updateNextReminderWidget()` (sender of the widget refresh broadcast), build
toolchain and dependency versions, `.github/workflows/test.yml`, documentation.
Out of scope: all other features, database schema, release signing/publishing, upstream
`Futsch1/medTimer`.

## 3. Changes (per commit)

| # | Commit | Area | Change | Risk | Verification |
|---|---|---|---|---|---|
| 1 | `d1b91ef5` | docs | Baseline measurement | none | — |
| 2 | `683c559c` | docs | Impact assessment IMP-01…14, IMP-T1…T8 | none | — |
| 3 | `14eb684b` | toolchain | Gradle 9.7.1, AGP 9.4.1 (built-in Kotlin 2.4.20, `org.jetbrains.kotlin.android` and `kotlinOptions` removed), safeargs 2.10.2, Play publisher 4.1.1, SonarQube plugin disabled (fails to apply with AGP 9.4: "No Android variant found for project app") | medium (build system) | `assembleDebug`, `testDebugUnitTest` 56/56 |
| 4 | `2ef00e46` | dependencies | Room 2.8.5, Robolectric 4.17, AndroidX/Material/test libraries, gson; `material.R.attr.colorPrimary` → `appcompat.R.attr.colorPrimary` at 4 call sites (attribute removed from Material 1.14 R) | medium (runtime libs) | `assembleDebug`, 56/56 tests, lint 19 → 5 |
| 5 | `ecdd3cad` | SDK | `compileSdk 37`, core-ktx 1.19.1 | low | `assembleDebug`, 56/56, lint 5 → 2 |
| 6 | `9f654adc` | SDK | `targetSdk 37`, Gradle 9.8.1 | medium (opts into Android 17 behaviour) | `assembleDebug`, 56/56, lint 0 |
| 7 | `7b87fbcf` | widgets (IMP-07) | Refresh broadcast uses the manifest-declared action via `WidgetUpdateReceiver.ACTION_WIDGET_UPDATE` | low | `WidgetUpdateReceiverTest`, `am broadcast` on API 35 |
| 8 | `9e6c52ab` | widgets (IMP-01) | `WidgetImpl` split into `buildLargeRemoteViews/buildSmallRemoteViews/buildRemoteViews` (same actions), KDoc on the Android 17 limit; `WidgetImplTest` | low (refactor, behaviour-preserving) | `WidgetImplTest` 7 tests; API 35 widget screenshots |
| 9 | `26af922c` | widgets/tests | Injectable loaders in the two line providers (default = previous repository code); `WidgetLineProviderTest` | low | 14 tests |
| 10 | `cd1de20a` | tests | `WidgetUpdateReceiverTest` | none | 2 tests |
| 11 | `818f0515` | docs | `docs/upgrade/test-evidence.md` | none | — |
| 12 | `793d6a86` | CI | `test.yml` `target_api_level` 34 → 37 | low | CI run (see verification report) |
| 13 | (see `git log`) | dependencies (IMP-T6) | Espresso pinned at 3.6.1: 3.7.0 made 15/21 instrumented tests time out on `LooperIdlingResource-Export`; `main` and the espresso-only revert pass | low | `connectedDebugAndroidTest` API 35: 21/21 |
| 14 | (see `git log`) | docs | architecture, compliance drafts, CHANGELOG, README, evidence files | none | — |

## 4. Deviations and decisions

- **SonarQube plugin disabled on the branch** (commit 3): both 5.1.0.4882 and 7.3.0.8198 fail at
  apply time with AGP 9.4.1 in this project. The CI `SonarQube` step is conditional on
  `SONAR_TOKEN` and does not run on this fork. Re-enable when a compatible plugin release exists.
- **Robolectric SDK level 35, not 37** for the new JVM tests: Robolectric 4.17 requires a JDK 21
  test JVM for SDK 36/37 sandboxes; on JDK 21 two pre-existing tests fail for JDK-related reasons
  (details in `docs/upgrade/test-evidence.md` §4). Kept JDK 17; recorded as an open item.
- **Espresso pinned at 3.6.1**: see commit 13; lint keeps 4 `GradleDependency` findings for the
  espresso artifacts (recorded, not suppressed).
- **Dependency bump beyond "minimal"**: the 19 `GradleDependency` lint findings on the baseline
  make `lintDebug` fail; they were resolved by updating the versions (no suppression). This is
  within the ticket's instruction to record, not hide, pre-existing problems.
- **`core-ktx` staged**: 1.16.0 in the dependency commit (1.19.1 requires compileSdk ≥ 37), 1.19.1
  with the compileSdk commit.

## 5. Impact on users / data

No UI or behaviour change intended for users. No Room schema change (database version
unchanged, `schemas/` untouched). targetSdk 37 opts the app into Android 17 behaviour on devices
running Android 17; the impact assessment found no required code change in the widget module.
Other modules were not assessed in this ticket (open item).

## 6. Rollback

See `docs/upgrade-impact-android-17-widgets.md` §5 — per-commit reverts; the `targetSdk` commit
can be reverted alone.
