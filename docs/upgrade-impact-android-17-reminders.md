# EE-30 — Change impact assessment: reminder scheduling & notification module on Android 16/17 (targetSdk 37)

**DRAFT — input for human review. This document makes no claim of compliance with IEC 62304, FDA
regulations or any other standard.**

Scope: `com.futsch1.medtimer.reminders` (+ `reminders.scheduling`), the receivers `Autostart` and
`ReminderProcessor`, `ReminderNotificationChannel*`, `RequestPostNotificationPermission`, the exact-alarm
preference UI in `preferences/PreferencesFragment` and the manifest. Baseline: `main` @ `8fde0b91`
(v1.16.5, compileSdk/targetSdk 35, minSdk 28) — see `docs/upgrade/baseline.md`.

Sources read in full (fetched 2026-10-12):

* S16T https://developer.android.com/about/versions/16/behavior-changes-16
* S16A https://developer.android.com/about/versions/16/behavior-changes-all
* S17T https://developer.android.com/about/versions/17/behavior-changes-17
* S17A https://developer.android.com/about/versions/17/behavior-changes-all
* AGP  https://developer.android.com/build/releases/gradle-plugin (compatibility table)

Evidence column: `file:line` references are to the baseline commit; "grep" means a repository-wide
search over `app/src/main` (`*.java`, `*.kt`, `*.xml`) that returned no hit in the module.

Applies: **Yes** = code/manifest change needed · **No** = not affected, evidence given ·
**RT** = needs runtime check on an emulator (no code change expected).

## A. Behaviour changes — one row per change

| ID | Android | Change (source) | Applies | Evidence | Affected files | Risk | Planned fix | Verification |
|---|---|---|---|---|---|---|---|---|
| IMP-01 | 16 (all apps) | JobScheduler quota optimisations: jobs started from the background (incl. WorkManager) get a runtime quota; `getStopReason()` may return `STOP_REASON_TIMEOUT`/quota (S16A) | **RT** | All module work is `OneTimeWorkRequest` enqueued from `ReminderProcessor.onReceive` after an alarm or notification action — `ReminderProcessor.java:141-177`; `Autostart.java:35-40`; `WorkManagerAccess.java` builds a single-thread executor. Jobs are short (DB + `notify()`), no periodic work (grep `PeriodicWorkRequest`: none). Alarm-triggered work is exempt from the quota per S16A ("jobs started … in response to an alarm … are not affected"). | `app/build.gradle.kts` (WorkManager version) | M — a quota stop would delay a reminder | Bump `androidx.work:work-runtime` to the current stable (S16A recommends ≥ 2.10.x for the quota handling); no code change | Emulator API 35 flow (alarm → notification within the expected minute); CI instrumented run on API 37 |
| IMP-02 | 16 (target 36+) | Fixed-rate work scheduling optimisation (`scheduleAtFixedRate` missed executions collapsed) (S16T) | **No** | grep `scheduleAtFixedRate`, `ScheduledExecutorService`: none in `app/src/main` | — | L | none | — |
| IMP-03 | 16 (all) | Ordered broadcast priority no longer globally ordered across processes; `android:priority` ignored across apps (S16A) | **No** | Neither receiver declares `android:priority`; both are `exported="false"` and only receive app-internal explicit intents — `AndroidManifest.xml:63-85` (grep `android:priority`: none) | — | L | none | — |
| IMP-04 | 16 (all) | Abandoned-job detection (JobService that never calls `jobFinished`) (S16A) | **No** | Module has no own `JobService`; WorkManager's `SystemJobService` manages the lifecycle (grep `JobService`: none) | — | L | none (covered by WorkManager bump, IMP-01) | — |
| IMP-05 | 16 (target 36+) | Edge-to-edge: `windowOptOutEdgeToEdgeEnforcement` is ignored, apps are drawn edge-to-edge (S16T) | **RT** (UI outside module) | The module's only UI is the custom-snooze path, which opens `MainActivity` (`Notifications.java` snooze action → `ReminderProcessor.getCustomSnoozeIntent`); no `windowOptOutEdgeToEdgeEnforcement` in `res/values*/themes.xml` (grep: none) — the app does not opt out, so nothing changes at target 37 | — (would be `MainActivity`/themes, out of module) | L | none in module; flagged for the app-shell owner | API 35 screenshots of the snooze flow in `docs/upgrade/evidence/`; API 37 UI could not be captured on this VM (SurfaceFlinger crash-loop, see §C) |
| IMP-06 | 16 (target 36+) | Predictive back enabled by default; `onBackPressed`/`KEYCODE_BACK` no longer delivered (S16T) | **No** | `AndroidManifest.xml:28` already sets `android:enableOnBackInvokedCallback="true"` on the baseline; `MainActivity.java:109` uses `OnBackPressedDispatcher`; no `onBackPressed()` override or `KEYCODE_BACK` handling in the module (grep: none) | — | L | none | existing instrumented tests (`connectedDebugAndroidTest`) |
| IMP-07 | 16 (opt-in via `intentMatchingFlags`; S16T announces enforcement in a future release) / 17 | Safer Intents: explicit intents must match the target component's intent filter; intents without action no longer match (S16T) | **Yes** | `ActivityCodes.java:18` defines `SNOOZE_ACTION = "com.futsch1.medTimer.SNOOZE_ACTION"`, built explicitly in `ReminderProcessor.getSnoozeIntent` (`:71-79`), but the `ReminderProcessor` `<intent-filter>` (`AndroidManifest.xml:80-85`) only lists `REMINDER_ACTION`, `RESCHEDULE_ACTION`, `DISMISSED_ACTION`, `TAKEN_ACTION`. With Safer-Intents enforcement the Snooze notification action would be dropped. All other module intents already match their filter. | `app/src/main/AndroidManifest.xml` | M — losing the Snooze action would silently break a user-visible path | Add `<action android:name="com.futsch1.medTimer.SNOOZE_ACTION"/>` to the `ReminderProcessor` filter (manifest-only; behaviour identical today) | New JVM test asserting every action produced by `ReminderProcessor` factory methods is declared in the merged manifest filter; API 35 emulator Snooze tap |
| IMP-08 | 16 (opt-in) / 17 (target 37: mandatory) | Local-network access requires `ACCESS_LOCAL_NETWORK` (S16T/S17T) | **No** | App removes `INTERNET` and `ACCESS_NETWORK_STATE` from the merged manifest — `AndroidManifest.xml:12-17` (`tools:node="remove"`); grep `Socket`, `HttpURLConnection`, `okhttp`: none | — | L | none | lint/merged-manifest check |
| IMP-09 | 16 (all) | ART internal changes / 16 KB page size (S16A) | **No** | Pure Java/Kotlin app, no `jniLibs`, no native code (grep `System.loadLibrary`, `externalNativeBuild`: none) | — | L | none | build |
| IMP-10 | 16 (all) | Intent-redirection protection: nested intents from untrusted sources are blocked unless `removeLaunchSecurityProtection()` (S16A) | **No** | Module never embeds an `Intent` as an extra (grep `putExtra(.*Intent` / `getParcelableExtra`: none); all extras are primitives (`ReminderProcessor.java:48-114`) | — | L | none | — |
| IMP-11 | 17 (target 37) | Memory limits for custom notification views / widget `RemoteViews` (S17T) | **No** (module) / flagged | Notifications use `NotificationCompat.Builder` with standard styles only — `Notifications.java` (grep `RemoteViews`, `setCustomContentView`, `DecoratedCustomViewStyle` in `reminders/`: none). `RemoteViews` is used only by `widgets/WidgetImpl.kt:30-78` (outside this module — widget bitmap budget 1.5×w×h×4 bytes applies there) | — | L | none in module; widget owner to review `WidgetImpl.kt` | API 35 notification screenshot |
| IMP-12 | 17 (target 37) | Lock-free `MessageQueue`: reflection/`Looper.getQueue()` internals change, `MessageQueue.next()`/`enqueueMessage` not overridable (S17T) | **No** | Module uses `HandlerThread`/`Handler` through public API only — `Autostart.java:47-50`; grep `MessageQueue`, `getQueue(`: none | — | L | none | JVM tests (Robolectric) + emulator |
| IMP-13 | 17 (target 37) | `static final` fields unmodifiable via reflection/JNI (S17T) | **No** | grep `setAccessible`, `Field.set(`, `getDeclaredField(`: none in module. `database/JSONBackup.java:81` reads `getDeclaredFields()` of POJOs but never writes (no `.set(`) | — | L | none | JVM tests of backup unaffected (existing) |
| IMP-14 | 17 (target 37) | Background-activity-launch hardening: `PendingIntent` sender/creator rules, `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE` default (S17T) | **No** | The only activity `PendingIntent` is the notification content/custom-snooze intent (`Notifications.java`, `getActivity(... FLAG_IMMUTABLE|FLAG_UPDATE_CURRENT)`), launched by a user tap on a notification — exempt as a user-initiated launch. No `startActivity` from workers/receivers (grep in `reminders/`: none) | — | L | none | API 35 tap on notification opens the app |
| IMP-15 | 17 (target 37) | Background-audio hardening: apps without an exemption cannot start audio playback from the background; exact-alarm (`SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`) holders and `USAGE_ALARM` streams are exempt (S17T) | **No** | The app does not play audio itself — the notification channel sound is played by the system (`ReminderNotificationChannel.kt:28-45`). `NotificationSoundManager.kt` only toggles `AudioManager` ringer mode (grep `MediaPlayer`, `Ringtone`, `SoundPool`, `AudioTrack`: none) | — | L | none | API 35 notification sound heard = system-played |
| IMP-16 | 17 (target 37) | Large-screen orientation/resizability opt-outs removed (S17T) | **No** (module) | Module has no activity; app-shell concern | — | L | none | — |
| IMP-17 | 17 (all) | App memory limits (per-app RSS cap, `ActivityManager.getMemoryInfo`) (S17A) | **No** | Workers are small and short-lived; no bitmaps/large buffers in module (grep `BitmapFactory` in `reminders/`: none) | — | L | none | — |
| IMP-18 | 17 (all) | `android:usesCleartextTraffic` deprecation path (S17A) | **No** | Already `false` — `AndroidManifest.xml:33`; no network | — | L | none | — |
| IMP-19 | 17 (target 37) | Content Transparency / `android:hideFromAccessibility`-style password hiding, SMS OTP permission, a11y IME, ECH, native dynamic code loading, CP2 access, `setContentCaptureEnabled` deprecation, Bluetooth RFCOMM, touchpad, keystore limits, cross-profile loopback (S17T/S17A, remaining rows) | **No** | None of these APIs are referenced in the module (grep `SEND_SMS`, `RECEIVE_SMS`, `Bluetooth`, `ContactsContract`, `setContentCaptureEnabled`, `DexClassLoader`, `KeyStore`: none in `app/src/main` except `tools:node="remove"` permissions) | — | L | none | — |
| IMP-20 | 14→17 (unchanged in 16/17) | Exact-alarm policy: `SCHEDULE_EXACT_ALARM` not pre-granted for target 33+; `canScheduleExactAlarms()` must be checked; `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` (S16A/S17A list no new change) | **RT** (regression guard) | Already handled: `RescheduleWork.java:106-110` (`setExactAndAllowWhileIdle` vs `setAndAllowWhileIdle`), `:143-148` (`EXACT_REMINDERS` pref && SDK ≥ 31 && `canScheduleExactAlarms()`); `PreferencesFragment.java:59-130,183-190` (request dialog). Baseline JaCoCo: the exact branch is **not** covered by any JVM test | `RescheduleWork.java` (test only) | M — core timing path, currently untested | No production change; add JVM tests for both branches | New tests `RescheduleWorkExactAlarmUnitTest`; API 35 flow with exact reminders ON |
| IMP-21 | 13→17 (unchanged) | `POST_NOTIFICATIONS` runtime permission (target 33+) | **No** (already handled) | `AndroidManifest.xml:7`; `RequestPostNotificationPermission.kt` requests at first start; no change in S16*/S17* | — | L | none | API 35 flow: permission dialog granted |
| IMP-22 | 12→17 (unchanged) | `PendingIntent` mutability must be explicit (target 31+) | **No** (already handled) | `PendingIntentBuilder.java:30-35` and all `Notifications.java` PendingIntents use `FLAG_IMMUTABLE` (grep `FLAG_MUTABLE`: none; every `PendingIntent.get*` call carries `FLAG_IMMUTABLE`) | — | L | none | New JVM test asserting intent extras/flags for notification actions |
| IMP-23 | 8→17 (unchanged) | Boot receiver / implicit broadcast limits; `BOOT_COMPLETED` & `MY_PACKAGE_REPLACED` still delivered to manifest receivers | **No** (already handled) | `AndroidManifest.xml:63-72` (`Autostart`, `exported="false"`, permission `RECEIVE_BOOT_COMPLETED`); `Autostart.java:30-42`. Baseline JaCoCo: 0 % covered | `Autostart.java` (test only) | M — untested reboot recovery path | No production change; add JVM test | New test `AutostartUnitTest` |
| IMP-24 | 8→17 (unchanged) | Notification channels: importance/sound/DND bypass set at channel creation cannot be changed by the app afterwards | **No** | `ReminderNotificationChannel.kt`, `ReminderNotificationChannelManager.kt` — no new S16/S17 channel rule | — | L | none | API 35 screenshot of channel settings |

## B. Not affected — summary with evidence

IMP-02, 03, 04, 06, 08, 09, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 21, 22, 23, 24 — each row above
cites the file/line or the empty grep. No row is marked "not affected" from memory.

## C. Items that need a runtime check (no code change expected)

* IMP-01 (JobScheduler quota) and IMP-20 (exact alarm) — API 35 emulator flow + API 37 CI instrumented run.
* IMP-12 (lock-free `MessageQueue`) — production code is unaffected, but the *test* stack is: Espresso 3.7.0
  (which replaces its `MessageQueue` reflection with `TestLooperManagerCompat`, i.e. the Android 17-ready path)
  never reported the app's `OptionsMenu` "Export" `HandlerThread` looper (registered via
  `IdlingRegistry.registerLooperAsIdlingResource`) as idle on the API 35 emulator → 15/21 instrumented tests timed
  out. Espresso is kept at 3.6.1 on this branch (21/21 pass on API 35). Whether 3.6.1's reflection still works on
  an API 37 device is answered by the CI run on API 37, not guessed here.
* IMP-05 (edge-to-edge) — API 35 screenshots only. **Known limitation:** the API 36/37 system images
  crash-loop SurfaceFlinger under the headless emulator on this VM, so API 37 evidence is limited to
  build + `adb install`; UI behaviour on a real Android 17 device/CI emulator remains to be confirmed by a human.

## D. Planned production changes (one commit each, `IMP-xx:` prefix)

| Commit | Change | Behaviour impact |
|---|---|---|
| toolchain | Gradle wrapper 8.11.1 → 9.x, AGP 8.9.0 → 9.4.x (first AGP line documented for API 37; Gradle ≥ 9.6.0, Build-Tools ≥ 36), Kotlin plugin handling per AGP 9 built-in Kotlin | none |
| deps | WorkManager, Room, Core-KTX, AppCompat, Material, Navigation, Lifecycle, test libs to current stable (also clears the 19 pre-existing `GradleDependency` lint errors) | none expected; Room schema unchanged |
| compileSdk 37 | `compileSdk = 37` | none |
| targetSdk 37 | `targetSdk = 37` | activates IMP-07/08/11-16 rules at runtime |
| IMP-07 | add `SNOOZE_ACTION` to `ReminderProcessor` intent filter | none today; prevents Snooze loss under Safer Intents |

Rows IMP-20/22/23 add **tests only**. No lint suppression, no test deletion.

## E. Rollback plan

* Every step is its own commit on `devin/ee-30-android17-reminders`; `git revert <sha>` in reverse order
  (IMP-07 → targetSdk → compileSdk → deps → toolchain) restores the baseline without data migration,
  because no Room schema/version changes and no persisted-preference changes are made.
* `targetSdk` can be reverted alone (37 → 35) to disable all target-gated Android 16/17 behaviours while
  keeping compileSdk 37 and the dependency bumps; the module code is identical under both targets.
* Release gating: do not ship until the API 37 CI instrumented run and a manual Android 17 device check
  (notification received, Snooze/Taken, reboot reschedule) have been signed off by a human.
