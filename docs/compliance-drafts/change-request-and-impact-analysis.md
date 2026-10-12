# Change request and impact analysis - Android 17 (API 37) upgrade

**DRAFT for human review.** Engineering input prepared by Devin for a regulated software team; it does not claim compliance
with IEC 62304, FDA guidance or any other standard and must be reviewed, completed and approved by the responsible humans.

## 1. Change request

| Field | Value |
|---|---|
| Ticket | [EE-29 - Upgrade MedTimer from Android 15 (API 35) to Android 17 (API 37)](https://cog-gtm.atlassian.net/browse/EE-29) |
| Repository / branch | `eashansinha/medTimer`, `devin/ee-29-android-17-upgrade` → `main` (v1.16.5 baseline) |
| Type | Platform / toolchain upgrade (no functional feature change intended) |
| Requested change | `compileSdk`/`targetSdk` 35 → 37, `minSdk` 28 unchanged; required toolchain and dependency updates; remediation of Android 16/17 behavior changes that affect the app; improved automated tests; documentation |
| Motivation | Keep the app installable/publishable on current Android versions and opt into Android 16/17 runtime behaviour in a controlled way |
| Session | https://devin-gtm.devinenterprise.com/sessions/a39f96483e3d4a068d05a5488c21960f |

## 2. Impact analysis

The detailed, per-behavior-change analysis (51 rows, IMP-01 … IMP-51, with code evidence, risk, planned fix and
verification) is in [`docs/upgrade-impact-android-17.md`](../upgrade-impact-android-17.md). Summary:

| Category | Items | Result |
|---|---|---|
| Applies, code change made | IMP-01 edge-to-edge opt-out removal; IMP-39 / IMP-50 background audio hardening | Fixed on the branch (commits `c977631e`, `211b0e89`) |
| Applies, handled by dependency/toolchain update | IMP-14 JobScheduler quota (WorkManager 2.12.0); IMP-02/IMP-20 predictive back (already AndroidX callback based); IMP-21 themed icons (monochrome layer present) | No source change needed |
| Needs runtime check (cannot be fully verified on the development VM) | IMP-05 large-screen restrictions, IMP-25 RemoteViews memory limit, IMP-27 static-final reflection, IMP-33 activity security, IMP-42 memory limits, IMP-48 IME after rotation | Covered by instrumented tests on API 37 in CI where possible; otherwise listed as open items |
| Not applicable, with evidence | All remaining IMP rows (local network, SMS OTP, Keystore limits, Certificate Transparency, Bluetooth, MediaStore, Contacts, native code loading, …) | Search evidence in the impact document |

### 2.1 Affected software items

| Item | Files | Change |
|---|---|---|
| Build configuration | `build.gradle.kts`, `app/build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties` | Toolchain, SDK levels, dependency versions, JaCoCo tasks |
| UI shell | `MainActivity.java`, `res/layout/activity_main.xml` | Explicit window-insets handling (IMP-01) |
| Reminder delivery | `reminders/NotificationSoundManager.kt` | Restore ringer only if unmute took effect (IMP-39/50) |
| Theme attribute lookups | `MedTimerAppIntro.kt`, `overview/OverviewFragment.java`, `statistics/TakenSkippedChart.java`, `statistics/MedicinePerDayChart.java` | `material.R.attr.colorPrimary` → `appcompat.R.attr.colorPrimary` (Material 1.14) |
| Tests | `app/src/test/.../RescheduleWorkUnitTest.java`, `AutostartUnitTest.java`, `NotificationSoundManagerUnitTest.java` | New JVM tests |
| CI | `.github/workflows/test.yml` | API 37 emulator, launch recording artifact |
| Documentation | `README.md`, `CHANGELOG.md`, `docs/**` | New/updated |

### 2.2 Risk assessment (engineering view)

| Risk | Level | Mitigation / residual |
|---|---|---|
| Reminder not delivered or delivered at the wrong time after upgrade | High impact / low likelihood | Scheduling code unchanged; `RescheduleWorkUnitTest` + existing scheduler tests; instrumented `NotificationTest` ×5 on API 35 and (CI) API 37 |
| Alarm permission behaviour on Android 17 | Medium | `canScheduleExactAlarms()` fallback already present, now unit-tested |
| "Override DND" sound no longer unmutes on Android 17 | Medium | IMP-39/50 fix prevents wrong ringer-mode restore; the feature itself may be silently limited by the OS - open item, needs device test |
| Layout under system bars / cutout on Android 16+ | Medium | IMP-01 insets listener; API 35 screenshots; API 37 CI recording |
| Regression from library major updates (Room 2.8, Material 1.14, Navigation 2.10) | Medium | Full unit suite (67 tests) and instrumented suite executed; see verification report |
| Toolchain regressions (AGP 9 built-in Kotlin, JaCoCo class dirs) | Low | Build, lint and coverage verified; JaCoCo path fixed |
| Lint not fully green | Low | 1 remaining `NewerVersionAvailable` (sonarqube) vs 19 errors on baseline; documented |

## 3. Verification and traceability

See [`verification-report.md`](verification-report.md), [`traceability-matrix.md`](traceability-matrix.md) and
[`../upgrade/test-evidence.md`](../upgrade/test-evidence.md).

## 4. Rollback

Revert the merge commit of the PR (all changes are on one branch); `main` stays at v1.16.5 / API 35. The Room schema is
unchanged (no database migration was added), so downgrading the app build does not require data migration.

## 5. Approvals (to be completed by humans)

| Role | Name | Date | Decision |
|---|---|---|---|
| Change owner | | | |
| Software lead | | | |
| QA | | | |
| Regulatory / quality (if applicable) | | | |
