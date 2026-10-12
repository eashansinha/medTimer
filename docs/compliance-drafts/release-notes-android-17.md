# Release Notes (draft) — MedTimer 1.17 / Android 17 upgrade

**Status: DRAFT for human review.** Not a compliance statement for IEC 62304, FDA guidance or any other standard. Ticket: [EE-32](https://cog-gtm.atlassian.net/browse/EE-32). Branch: `devin/ee-32-android-17-upgrade`. Nothing in this branch has been merged or published.

## What changed

| Area | Before (v1.16.5, `main`) | After |
|---|---|---|
| `compileSdk` / `targetSdk` / `minSdk` | 35 / 35 / 28 | **37 / 37 / 28** |
| Gradle / AGP / Kotlin | 8.11.1 / 8.9.0 / 2.1.10 | 9.8.1 / 9.4.1 / 2.4.21 (AGP built-in Kotlin) |
| Room / Navigation / WorkManager | 2.6.1 / 2.8.9 / 2.10.0 | 2.8.5 / 2.10.2 / 2.12.0 |
| Material / AppCompat / core-ktx | 1.12.0 / 1.7.0 / 1.15.0 | 1.14.0 / 1.8.0 / 1.19.1 |
| Production code | — | 4 one-line changes: `colorPrimary` resolved via `androidx.appcompat.R.attr` (Material 1.14 R-class change). No behavior change intended. |
| Tests | 56 JVM, 21 instrumented | 65 JVM (+9), 23 instrumented (+2) |
| Build plugins | Play publisher 3.12.1, SonarQube 5.1 | Play publisher 4.1.1 (never executed), SonarQube removed |
| CI | API 34 emulator | API 37 `google_apis` x86_64 emulator + app-launch screen recording artifact |

Full dependency list: [`soup-list.md`](soup-list.md).

## Why

Android 16/17 behavior changes apply only once an app targets those API levels; the platform and store ecosystem expect current `targetSdk`. The change impact assessment ([`docs/upgrade-impact-android-17.md`](../upgrade-impact-android-17.md)) evaluated every listed Android 16 and 17 behavior change (IMP-01 … IMP-55) against the code base before any change was made.

## Risk

- Core reminder path (exact alarms → notification → actions) is unchanged in code; residual risk is platform behavior on real Android 17 devices, which could only be exercised on the GitHub Actions API 37 emulator (see verification report).
- Edge-to-edge enforcement and predictive back were already implemented in 1.16.5; both are now pinned by `Android17BehaviorTest`.
- Library major/minor bumps (Material 1.14, Room 2.8, WorkManager 2.12) carry regression risk in UI and persistence; mitigated by the full unit and instrumented suites plus the recorded manual flow, not by dedicated new tests for charts/PDF export.

## Verification evidence

See [`verification-report.md`](verification-report.md) and [`../upgrade/test-evidence.md`](../upgrade/test-evidence.md). Screenshots and the screen recording from the API 35 emulator are in [`../upgrade/evidence/`](../upgrade/evidence/).

## Known limitations

- **Android 17 UI could not be run on the build VM**: the API 37 system image crash-loops SurfaceFlinger under the headless emulator (gfxstream `hasReadColorBufferDma` assertion); the local API 37 AVD was used for `adb install` evidence only. Android 17 UI/instrumented evidence comes from the CI workflow.
- `TagTest.tagHandling` (instrumented) fails on the headless API 35 emulator before and after the upgrade (dialog button click times out) — pre-existing, unrelated to the upgrade.
- `androidx.lifecycle:lifecycle-extensions:2.2.0` is a deprecated artifact still referenced by the build.
- SonarQube analysis is no longer available from this branch's build.
- Tablet / large-screen layouts (IMP-05/IMP-41) were not exercised — no tablet AVD on the VM.

## Open items for the human reviewer

1. Decide whether to restore SonarQube once a plugin version compatible with AGP 9 is released.
2. Run the instrumented suite on a physical Android 17 device (notification tap, exact alarm, widget).
3. Review and, if accepted, finalize the DRAFT compliance documents in this directory.
4. Replace `lifecycle-extensions` with the individual lifecycle artifacts (out of scope here: no drive-by refactors).
5. Bump `versionName`/`versionCode` to 1.17.0 at release time (not done in this branch — no publishing).
