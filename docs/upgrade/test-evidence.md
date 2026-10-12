# EE-30 — JVM test evidence (before / after)

**Status: DRAFT for human review.** All numbers below come from the commands quoted in each section, run on
this branch (`devin/ee-30-android17-reminders`) on the same VM as `docs/upgrade/baseline.md` (JDK 17.0.20.1,
Android SDK at `$ANDROID_HOME`, platform `android-37`). Nothing in this document is extrapolated.

## Commands

```bash
# JVM unit tests (JUnit 5 + Robolectric 4.17, @Config(sdk = 34))
./gradlew testDebugUnitTest --console=plain

# Coverage from the unit-test execution data only (instrumented tests excluded on purpose)
./gradlew JacocoDebugCodeCoverage -x connectedDebugAndroidTest --console=plain
# report: app/build/reports/jacoco/JacocoDebugCodeCoverage/JacocoDebugCodeCoverage.xml
```

The baseline column repeats `docs/upgrade/baseline.md` (same commands on unchanged `main`, v1.16.5).

## Test counts

| | Baseline (`main`) | After (this branch) |
|---|---|---|
| JVM test classes | 12 | 19 (+7) |
| JVM tests executed | 56 | **91** (+35) |
| Failures / errors / skipped | 0 / 0 / 0 | **0 / 0 / 0** |
| Build result | `BUILD SUCCESSFUL` | `BUILD SUCCESSFUL` |

No existing test was changed, deleted or disabled.

## Coverage — overall and per package

| Scope | Lines before | Lines after | Branches before | Branches after |
|---|---|---|---|---|
| Whole app | 768 / 6934 (11.1 %) | **819 / 6794 (12.1 %)** | 260 / 2044 (12.7 %) | **277 / 2012 (13.8 %)** |
| `com.futsch1.medtimer.reminders` | 305 / 487 (62.6 %) | **329 / 487 (67.6 %)** | 55 / 144 (38.2 %) | **63 / 144 (43.8 %)** |
| `com.futsch1.medtimer.reminders.scheduling` | 145 / 150 (96.7 %) | 145 / 150 (96.7 %) | 90 / 95 (94.7 %) | 89 / 93 (95.7 %) |
| `com.futsch1.medtimer` (root pkg, incl. `Autostart`) | 28 / 601 (4.7 %) | **55 / 620 (8.9 %)** | 2 / 162 (1.2 %) | **13 / 164 (7.9 %)** |

Totals differ slightly (6934 → 6794 lines, 95 → 93 scheduling branches) because the toolchain upgrade
(AGP 9.4.1 built-in Kotlin, Kotlin 2.2.x) emits different bytecode for the same Kotlin sources; the
sources of the scheduling package were not changed.

## Coverage — touched / targeted classes

| Class | Lines before → after | Branches before → after | New tests |
|---|---|---|---|
| `Autostart` | 1 / 26 (3.8 %) → **26 / 26 (100 %)** | 0 / 10 (0 %) → **10 / 10 (100 %)** | `AutostartUnitTest` (IMP-23) |
| `reminders.RescheduleWork` | 27 / 56 (48.2 %) → **47 / 56 (83.9 %)** | 3 / 12 (25 %) → **10 / 12 (83.3 %)** | `RescheduleWorkExactAlarmUnitTest` (IMP-20) |
| `reminders.RepeatReminderWork` | 18 / 18 (100 %) → 18 / 18 (100 %) | 1 / 2 (50 %) → **2 / 2 (100 %)** | `RepeatReminderWorkLimitUnitTest` |
| `reminders.SnoozeWork` | 13 / 13 (100 %) → 13 / 13 (100 %) | – | `SnoozeWorkBoundsUnitTest` (IMP-20) |
| `reminders.ReminderProcessor` | 46 / 95 (48.4 %) → 46 / 95 (48.4 %) | 4 / 14 → 4 / 14 | `ReminderProcessorIntentUnitTest` (IMP-07/22) — covers the intent factories that were already covered; the manifest-filter assertion is the new protection |
| `reminders.PendingIntentBuilder` | 12 / 12 (100 %) → 12 / 12 (100 %) | – | (unchanged) |
| `reminders.scheduling.StandardScheduling` | 68 / 68 (100 %) → 68 / 68 (100 %) | 48 / 48 → 48 / 48 | `ReminderSchedulerDstMonthBoundaryUnitTest`, `ReminderSchedulerCyclicUnitTest` — new *scenarios* (DST gap/overlap, month/leap/year boundary, cycle wrap) on already-covered lines |
| `reminders.scheduling.IntervalScheduling` | 28 / 28 (100 %) → 28 / 28 (100 %) | 13 / 14 → 13 / 14 | (existing `ReminderSchedulerIntervalUnitTest.kt`) |
| `reminders.Notifications` | 65 / 98 (66.3 %) → 65 / 98 (66.3 %) | 13 / 32 → 13 / 32 | not targeted (UI/notification-builder code; covered by emulator flow) |

## New test classes (all under `app/src/test/java/com/futsch1/medtimer/`)

| Test class | Tests | What would make it fail |
|---|---|---|
| `ReminderSchedulerDstMonthBoundaryUnitTest` | 7 | wrong UTC instant across the Europe/Berlin spring-forward / fall-back change (incl. 02:30 gap and overlap, 25 h day), wrong next day at month end, 29 Feb, 31 Dec |
| `ReminderSchedulerCyclicUnitTest` | 4 | cyclic reminder (2 on / 1 off) fired on a pause day or skipped an active day, incl. second-cycle wrap |
| `SnoozeWorkBoundsUnitTest` | 5 | default ≠ 15 min, 1 min / 24 h snooze mis-computed, exact alarm used without permission or inexact used although allowed |
| `RescheduleWorkExactAlarmUnitTest` | 4 | `setExactAndAllowWhileIdle` chosen when `canScheduleExactAlarms()` is false or preference off, permission queried although preference off, alarm not cancelled when no medicines |
| `RepeatReminderWorkLimitUnitTest` | 5 | remaining repeats not decremented to exactly 0 / n-1 / stays negative for "unlimited", repeat alarm not at now + repeatTimeSeconds, update called although event missing |
| `AutostartUnitTest` | 5 | `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` do not enqueue the unique `reschedule` `RescheduleWork`, unrelated intents enqueue work, RAISED event not restored as `ReminderWork`, TAKEN/SKIPPED restored |
| `ReminderProcessorIntentUnitTest` | 5 | action/extras/component of the reminder, snooze, taken, dismissed intents wrong; **any action produced by `ReminderProcessor` missing from its manifest `<intent-filter>`** (this test fails on `main`, where `SNOOZE_ACTION` was undeclared — IMP-07) |

Each new test was observed failing at least once during development (wrong expectation or mock setup) before
being green, i.e. none of them is a tautology.

## Notes / limitations

- JaCoCo `classDirectories` had to be pointed at `build/intermediates/built_in_kotlinc/` because AGP 9's
  built-in Kotlin no longer writes `build/tmp/kotlin-classes/`; without this change Kotlin classes reported
  0 % (stale class files). The baseline numbers were produced with the old layout and are unaffected.
- Robolectric tests run with `@Config(sdk = 34)` (Robolectric 4.17 does not ship an API 37 android-all jar);
  OS-version-dependent branches above API 34 are therefore verified on the emulators, not on the JVM.
- Instrumented (`connectedDebugAndroidTest`) results are reported separately in
  `docs/compliance-drafts/EE-30-verification-report.md` with their API level.
