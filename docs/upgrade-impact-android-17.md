# Change Impact Assessment — MedTimer Android 15 (API 35) → Android 17 (API 37)

**Ticket:** [EE-32](https://cog-gtm.atlassian.net/browse/EE-32) · **Status:** DRAFT for human review · **Written before any code change** on `main` @ `8fde0b91` (v1.16.5).

Sources read (fetched 2026-10-12 from developer.android.com, saved offline during the session):

- A16: https://developer.android.com/about/versions/16/behavior-changes-16
- A16-all: https://developer.android.com/about/versions/16/behavior-changes-all
- A17: https://developer.android.com/about/versions/17/behavior-changes-17
- A17-all: https://developer.android.com/about/versions/17/behavior-changes-all
- AGP: https://developer.android.com/build/releases/about-agp , https://developer.android.com/build/releases/gradle-plugin , https://developer.android.com/build/releases/past-releases/agp-9-0-0-release-notes

"Evidence" = `file:line` on `main`, or the `grep -rn -E '<pattern>' app/src/main` that returned 0 hits. "Applies" is one of **Yes / No / Needs runtime check**.

## 1. Toolchain

| Item | Before (`main`) | After (planned) | Why / source |
|---|---|---|---|
| Android Gradle Plugin | 8.9.0 | **9.4.1** | Newest stable on Google Maven (`com.android.tools.build:gradle` metadata lists …, 9.4.0, 9.4.1, then 9.5.0-alphaNN). about-agp: "The maximum API level that AGP 9.4 supports is API level 37"; API 37 requires AGP ≥ 9.1.1. |
| Gradle wrapper | 8.11.1 | **9.8.1** | AGP 9.4 compatibility table: minimum 9.6.0; lint `AndroidGradlePluginVersion` (warningsAsErrors) demands the newest 9.8.1, verified to build. |
| SDK Build-Tools | 35.0.0 | 36.0.0 min (37.0.0 installed) | AGP 9.4 table: minimum 36.0.0. |
| JDK | 17 | 17 | AGP 9.4 table: JDK 17. |
| Kotlin Gradle plugin | 2.1.10 (`org.jetbrains.kotlin.android`) | **2.4.21** (latest stable on Maven Central; AGP 9 docs example pins 2.4.10) | AGP 9 has *built-in Kotlin*: the `org.jetbrains.kotlin.android` plugin is no longer applied; `kotlinOptions { jvmTarget }` → `kotlin { compilerOptions { jvmTarget } }`. |
| Room | 2.6.1 (plugin + runtime + `annotationProcessor` compiler) | **2.8.5** | Latest stable; Room 2.6.x predates AGP 9 built-in Kotlin. |
| Navigation Safe Args | 2.8.9 | **2.10.2** | Latest stable; needed for the AGP 9 variant API. |
| Robolectric | 4.14.1 | **4.17** | Maven Central has `android-all-instrumented` `17-robolectric-15733970-i7` (SDK 37 jar), so 4.17 can run tests against API 37 when `@Config(sdk=...)` is raised. |
| JaCoCo | Gradle default | **0.8.15** (`jacoco { toolVersion }`) | Needed for class files produced by newer Kotlin/Java toolchains. |
| JUnit Jupiter / Mockito / jazzer | 5.12.0 / 5.16.0 / 0.24.0 | unchanged | not flagged by lint. |
| Espresso (core/contrib/idling) | 3.6.1 | **3.6.1 kept** | 3.7.0 was tried: `LooperIdlingResource` for the debug-only `Export` HandlerThread (`OptionsMenu.java:163`) never becomes idle and every instrumented test hangs; reverted with evidence (see test-evidence.md). Open item. |
| `tech.apter…robolectric-extension-gradle-plugin` 0.9.0 | present | **kept (0.9.0)** | Latest on the plugin portal (checked 2026-10-12); configures and runs the JUnit 5 + Robolectric tests under AGP 9.4.1 / Gradle 9.8.1. |
| `com.github.triplet.play` 3.12.1 | present, unused in CI | **4.1.1** | 3.12.1 fails under AGP 9 (`BaseAppModuleExtension does not exist`); 4.1.1 configures. Never executed (no credentials, "no publishing"). |
| `org.sonarqube` 5.1.0.4882 | present, optional CI step | **removed on this branch** | Lint `NewerVersionAvailable` flags 5.1.0.4882; 7.5.0.8588 fails to configure under AGP 9 (`Could not create task :app:sonarResolver - No Android variant found for project app`). Non-essential; CI step removed too. Open item for the maintainers. |
| AppIntro 6.3.1 via jitpack.io | present | unchanged | JitPack reachable (HTTP 200 checked); retry on flake. |

Known AGP 9 migration items (AGP 9.0.0 release notes): built-in Kotlin & removal of `kotlinOptions`; `android.builtInKotlin` flag; new DSL/variant API (`android.newDsl`, opt-out `android.disallowGradleVariantApi`); deprecated `lifecycle-extensions:2.2.0` artifact (still resolves).

## 2. Module / package impact map (single module `app`)

| Package / area | Files | Behavior changes touching it |
|---|---|---|
| `com.futsch1.medtimer` (MainActivity, Autostart, OptionsMenu, Biometrics, BackupManager) | `MainActivity.java`, `Autostart.java`, `OptionsMenu.java`, `Biometrics.kt`, `BackupManager.java` | IMP-01, IMP-02, IMP-20, IMP-27, IMP-34, IMP-46, IMP-47, IMP-53 |
| `reminders` (alarms, work, notifications, actions) | `RescheduleWork.java`, `ReminderProcessor.java`, `Notifications.java`, `NotificationAction.java`, `PendingIntentBuilder.java`, `*Work.java`, `NotificationSoundManager.kt`, `scheduling/*` | IMP-04, IMP-10, IMP-14, IMP-15, IMP-26, IMP-34, IMP-40, IMP-52 |
| `widgets` (RemoteViews) | `WidgetImpl.kt`, `NextRemindersWidgetProvider.kt`, `LatestRemindersWidgetProvider.kt`, `res/layout/widget_*.xml`, `res/xml/*_widget_info.xml` | IMP-26, IMP-43 |
| `database` (Room, JSON backup via Gson + reflection) | `JSONBackup.java`, `MedicineRoomDatabase.java`, entities | IMP-17, IMP-28 |
| `helpers` (file sharing/export) | `FileHelper.java` | IMP-10, IMP-46 |
| UI resources | `res/layout/activity_main.xml`, `res/values*/themes.xml` | IMP-01, IMP-05, IMP-41, IMP-49 |
| Manifest | `AndroidManifest.xml` | IMP-01, IMP-02, IMP-12, IMP-31, IMP-34, IMP-45, IMP-52, IMP-53 |
| Build | `build.gradle.kts`, `app/build.gradle.kts`, `gradle/wrapper/*`, `.github/workflows/test.yml` | §1 toolchain, IMP-55 |

## 3. Impact table

Risk legend: **High** = reminder may be missed / crash; **Medium** = visible UI defect or degraded UX; **Low** = no user-visible effect expected.

| ID | Android | Behavior change | Source | Applies | Evidence (`main`) | Affected files | Risk — reason | Planned fix | Verification |
|---|---|---|---|---|---|---|---|---|---|
| IMP-01 | 16 | Edge-to-edge opt-out going away (targetSdk 36+: `windowOptOutEdgeToEdgeEnforcement` ignored; `statusBarColor`/`navigationBarColor` have no effect; content drawn under system bars) | A16 §Edge to edge | **Yes** (already migrated — verify) | `MainActivity.java:52` already calls `EdgeToEdge.enable(this)` (androidx.activity) — *correction of the first draft, whose grep pattern `enableEdgeToEdge` missed the Java call*; `res/layout/activity_main.xml:7` root `ConstraintLayout` has `android:fitsSystemWindows="true"` so the root consumes the system-bar insets as padding; `res/values/themes.xml:6-7` `windowLightStatusBar`/`statusBarColor=transparent` (ignored from targetSdk 35 on, harmless); no `windowOptOutEdgeToEdgeEnforcement` (`grep` → 0 hits) | `MainActivity.java`, `activity_main.xml`, `themes.xml` | **Medium** if it regressed (toolbar / BottomNavigationView overlapped by system bars once targetSdk=37); no reminder-logic impact | No code change: the app already opts in explicitly, enforcement changes nothing | Instrumented `Android17BehaviorTest.edgeToEdgeInsetsKeepToolbarAndBottomNavigationVisible` (toolbar top ≥ status-bar inset, bottom nav above the navigation-bar inset) on API 35; API 37 screenshot/recording from the CI workflow (no local API 37 UI possible)
| IMP-02 | 16 | Migration or opt-out required for predictive back (system back animations on by default for targetSdk 36+; `onBackPressed`/`KEYCODE_BACK` no longer called unless opted out) | A16 §Predictive back | **Yes** (already migrated — verify) | `AndroidManifest.xml:30` `android:enableOnBackInvokedCallback="true"`; `MainActivity.java:103-113` uses AndroidX `OnBackPressedCallback` and re-dispatches via `postDelayed(…,20)`; `grep -rn 'onBackPressed()' app/src/main` → only the dispatcher call at `MainActivity.java:109`; no `KEYCODE_BACK` handling | `MainActivity.java` | **Low** — already on the AndroidX back dispatcher; the 20 ms re-post is the only ordering-sensitive piece | No code change planned; keep manifest flag | Instrumented `Android17BehaviorTest.systemBackReturnsToOverview` (navigate Medicines → back → Overview) on API 35; CI recording on API 37 |
| IMP-03 | 16 | Elegant font APIs deprecated and disabled | A16 | **No** | `grep -rn -E 'elegantTextHeight' app/src/main` → 0 hits | — | Low | none | — |
| IMP-04 | 16 | Fixed-rate work scheduling optimization (`setRepeating`/`scheduleAtFixedRate`/periodic jobs may be batched) | A16 | **No** | `grep -rn -E 'PeriodicWorkRequest\|setRepeating\|setInexactRepeating\|scheduleAtFixedRate' app/src/main` → 0 hits; reminders use one-shot `setExactAndAllowWhileIdle` (`RescheduleWork.java:107`) | — | Low | none | covered by IMP-52 tests |
| IMP-05 | 16 | Adaptive layouts: orientation, resizability and aspect-ratio restrictions ignored on ≥600 dp screens (opt-out available in 16) | A16 §Device form factors | **Needs runtime check** | `grep -rn -E 'screenOrientation\|resizeableActivity\|minAspectRatio\|maxAspectRatio\|setRequestedOrientation' app/src/main` → 0 hits (app never restricts orientation) | layouts | **Low** — no restrictions declared, so nothing is "ignored"; only risk is untested large-screen layouts | none | Manual rotate on API 35 emulator (phone); tablet layout is an open item (no tablet AVD on the VM) |
| IMP-06 | 16 | Health and fitness permissions (`BODY_SENSORS` → `health` permissions) | A16 | **No** | `grep -rn -E 'BODY_SENSORS\|health\.connect\|HealthConnect' app/src/main` → 0 hits | — | Low | none | — |
| IMP-07 | 16 | New intents for Bluetooth bond loss / encryption change | A16 | **No** | `grep -rn -i 'bluetooth' app/src/main` → 0 hits | — | Low | none | — |
| IMP-08 | 16 | New way to remove Bluetooth bond | A16 | **No** | same as IMP-07 | — | Low | none | — |
| IMP-09 | 16 | MediaStore version lockdown | A16 | **No** | `grep -rn 'MediaStore' app/src/main` → 0 hits (exports use `ACTION_CREATE_DOCUMENT`/FileProvider) | — | Low | none | — |
| IMP-10 | 16 | Safer intents (implicit intents must match an exported filter; action required) | A16 §Security | **No (explicit intents only)** | Internal intents are explicit: `PendingIntentBuilder.java:38` broadcast to `ReminderProcessor` via `setClass` (`ReminderProcessor.java:66,76,118`), `Notifications.java:83`/`ReminderProcessor.java:125,134` → `MainActivity.class`, `RescheduleWork.java:151` → `WidgetUpdateReceiver.class`; outbound implicit intents carry actions: `FileHelper.java:71` `ACTION_SEND`, `BackupManager.java:100` `ACTION_OPEN_DOCUMENT` | — | Low | none | Device flows in Step 5 (notification tap, share/export) |
| IMP-11 | 16 | GPU syscall filtering | A16 | **No** | no native code: `grep -rn -E 'System.loadLibrary\|jniLibs' app/src/main` → 0 hits; `unzip -l MedTimer-debug.apk \| grep -c '\.so$'` → 0 | — | Low | none | — |
| IMP-12 | 16 | Local Network Permission (opt-in in 16) | A16 §Privacy | **No** | `AndroidManifest.xml:11-13` removes `INTERNET` (`tools:node="remove"`); `grep -rn -E 'Socket\(\|NsdManager\|HttpURLConnection' app/src/main` → 0 hits | — | Low | none | — |
| IMP-13 | 16 | App-owned photos (photo picker) | A16 | **No** | no `MediaStore`/photo-picker usage (IMP-09) | — | Low | none | — |
| IMP-14 | 16-all | JobScheduler quota optimizations (runtime quota for jobs incl. WorkManager-backed) | A16-all §Core | **Needs runtime check** | WorkManager one-time requests enqueued from broadcast/alarm paths (`WorkManagerAccess.java`, `reminders/*Work.java`); jobs are short DB/notification tasks | `reminders/*Work.java` | **Low** — work is triggered by exact alarms and finishes in ms; quota exhaustion unlikely | bump `work-runtime` 2.10.0 → 2.12.0 | API 35 device flow (reminder fires → notification) + CI logcat on API 37 |
| IMP-15 | 16-all | Abandoned empty jobs stop reason | A16-all | **Needs runtime check** | WorkManager internal; app does not use JobScheduler directly (`grep -rn JobScheduler app/src/main` → 0) | — | Low | WorkManager bump (IMP-14) | same as IMP-14 |
| IMP-16 | 16-all | Ordered broadcast priority scope no longer global | A16-all | **No** | `grep -rn -E 'sendOrderedBroadcast\|abortBroadcast\|android:priority' app/src/main` → 0 hits | — | Low | none | — |
| IMP-17 | 16-all | ART internal changes (reflection on hidden/internal ART structures breaks) | A16-all | **No** | Reflection is limited to the app's own POJO fields via Gson/`getDeclaredFields` (`JSONBackup.java:81`); no `Unsafe`/hidden-API use | `JSONBackup.java` | Low | none | new JVM test `JSONBackupTest` exercising the reflective missing-field check |
| IMP-18 | 16-all | 16 KB page size compatibility mode | A16-all | **No** | APK contains 0 `.so` files (IMP-11) | — | Low | none | `unzip -l` check repeated on the final APK |
| IMP-19 | 16-all | Deprecating disruptive accessibility announcements | A16-all | **No** | `grep -rn -E 'announceForAccessibility\|TYPE_ANNOUNCEMENT' app/src/main` → 0 hits | — | Low | none | — |
| IMP-20 | 16-all | Predictive back in 3-button navigation | A16-all | **Yes (same mechanism as IMP-02)** | see IMP-02 | `MainActivity.java` | Low | none | IMP-02 test |
| IMP-21 | 16-all | Automatic themed app icons (monochrome generated if missing) | A16-all | **No action** | `res/mipmap-anydpi/logo.xml:5`, `logo_round.xml:5` already declare `<monochrome>` | — | Low | none | Launcher screenshot (API 35) |
| IMP-22 | 16-all | Virtual device owner overrides | A16-all | **No** | consumer phone app; no virtual-device APIs | — | Low | none | — |
| IMP-23 | 16-all | Improved security against Intent redirection | A16-all | **No** | `grep -rn -E 'getParcelableExtra\(.*Intent\|EXTRA_INTENT' app/src/main` → 0 hits (no nested intents) | — | Low | none | — |
| IMP-24 | 16-all | Companion apps no longer notified of discovery timeouts | A16-all | **No** | `grep -rn CompanionDevice app/src/main` → 0 hits | — | Low | none | — |
| IMP-25 | 16-all | Improved Bluetooth bond loss handling | A16-all | **No** | IMP-07 | — | Low | none | — |
| IMP-26 | 17 | Memory limit for widgets (`RemoteViews` bitmap memory capped; oversized updates rejected) | A17 §UI | **Needs runtime check** | `widgets/WidgetImpl.kt:30,38,46-50,78` builds `RemoteViews` from `widget_*.xml` layouts; `grep -rn -E 'setImageViewBitmap\|setBitmap' app/src/main/java/com/futsch1/medtimer/widgets` → 0 hits (only vector drawable resources) | `widgets/*`, `res/layout/widget_*.xml` | **Low** — text-only lines plus small vector icons; well below the limit | none; keep bitmaps out of widgets | Widget on API 35 home screen screenshot; API 37 CI logcat for `RemoteViews` errors |
| IMP-27 | 17 | New lock-free `MessageQueue` implementation | A17 §Core | **Needs runtime check** | 22 `Handler` usages, all standard `post`/`postDelayed` on `HandlerThread` loopers or the main looper (e.g. `OptionsMenu.java:70,146`, `OverviewFragment.java:97,140,147`, `MainActivity.java:107`); `grep -rn -E 'MessageQueue\|IdleHandler\|myQueue\|SyncBarrier' app/src/main` → 0 hits | background-thread users | **Low** — public `Handler` API semantics unchanged; only the 20 ms back-dispatch re-post (`MainActivity.java:107`) is ordering-sensitive | none | IMP-02 back-navigation test; unit tests on Robolectric SDK 37 |
| IMP-28 | 17 | Static final fields unmodifiable via reflection | A17 §Core | **No** | `JSONBackup.java:81-90` only **reads** fields (`f.get(pojo)`); `grep -rn -E 'setAccessible\|Field\.set\|\.set\(pojo\|Modifier\.' app/src/main` → 0 writes; Gson (2.12.1) does not write static finals | `JSONBackup.java` | Low | none; bump Gson to 2.14.0 | new JVM test `JSONBackupTest` (missing field → `JsonParseException`, complete JSON → object) |
| IMP-29 | 17 | Accessibility support for complex IME physical keyboard typing | A17 §Accessibility | **No** | no custom IME / accessibility service | — | Low | none | — |
| IMP-30 | 17 | Encrypted Client Hello enabled | A17 §Privacy | **No** | no network (IMP-12) | — | Low | none | — |
| IMP-31 | 17 | Local network permission **required** for apps targeting 17 | A17 §Privacy | **No** | no network (IMP-12) | — | Low | none | — |
| IMP-32 | 17 | Hiding passwords from physical devices (external displays) | A17 §Privacy | **No** | `grep -rn -E 'textPassword\|inputType="text(Web)?Password' app/src/main` → 0 hits; app lock is `BiometricPrompt` only (`Biometrics.kt`) | — | Low | none | — |
| IMP-33 | 17 | OTP protection for standard SMS messages | A17 §Privacy | **No** | `grep -rn -E 'SmsManager\|RECEIVE_SMS\|READ_SMS' app/src/main` → 0 hits | — | Low | none | — |
| IMP-34 | 17 | Activity security (stricter rules for launching activities from background / notification trampolines) | A17 §Security | **Needs runtime check** | Notifications open `MainActivity` through a `PendingIntent.getActivity` content intent (`Notifications.java:57,83`; `ReminderProcessor.java:125,134`); receivers never call `startActivity` (`grep -rn startActivity app/src/main/java/com/futsch1/medtimer/reminders` → 0) | `Notifications.java`, `ReminderProcessor.java` | **Medium** — a notification tap that silently fails would hide reminders from the user | none expected (no trampolines) | API 35 device flow: tap reminder notification → app opens; API 37 CI recording |
| IMP-35 | 17 | Certificate Transparency enabled by default | A17 §Security | **No** | no network (IMP-12) | — | Low | none | — |
| IMP-36 | 17 | Safer native dynamic code loading | A17 §Security | **No** | no native code (IMP-11) | — | Low | none | — |
| IMP-37 | 17 | Restrict PII fields in CP2 (Contacts) data view | A17 §Security | **No** | `grep -rn -E 'ContactsContract\|READ_CONTACTS' app/src/main` → 0 hits | — | Low | none | — |
| IMP-38 | 17 | Strict SQL checks in CP2 | A17 §Security | **No** | same as IMP-37 | — | Low | none | — |
| IMP-39 | 17 | Deprecation of `setContentCaptureEnabled` | A17 §Intelligence | **No** | `grep -rn ContentCapture app/src/main` → 0 hits | — | Low | none | — |
| IMP-40 | 17 | Background audio hardening | A17 §Media | **No** | `grep -rn -E 'MediaPlayer\|Ringtone\|SoundPool\|AudioTrack\|AudioFocus' app/src/main` → 0 hits; reminder sounds are `NotificationChannel` sounds (`NotificationSoundManager.kt`) | — | Low | none | API 35 notification flow |
| IMP-41 | 17 | Platform API changes: orientation/resizability/aspect-ratio **opt-out removed** | A17 §Device form factors | **No** | app never opted out and declares no restrictions (IMP-05) | — | Low | none | IMP-05 |
| IMP-42 | 17 | Consistent `BluetoothSocket.read()` for RFCOMM | A17 §Connectivity | **No** | IMP-07 | — | Low | none | — |
| IMP-43 | 17-all | App memory limits (per-app heap caps enforced) | A17-all §Core | **Needs runtime check** | small UI + Room app; largest allocations are chart bitmaps (`statistics/*Chart*`), widgets are text | — | Low | none | CI run on API 37: no OOM in logcat / test results |
| IMP-44 | 17-all | SMS OTP protection (all apps) | A17-all §Privacy | **No** | IMP-33 | — | Low | none | — |
| IMP-45 | 17-all | `usesCleartextTraffic` deprecation plan | A17-all §Security | **No** | `AndroidManifest.xml:36` `android:usesCleartextTraffic="false"`; no network | `AndroidManifest.xml` | Low | none | — |
| IMP-46 | 17-all | Restrict implicit URI grants (URIs in extras no longer implicitly granted) | A17-all §Security | **Needs runtime check** | `FileHelper.java:70-75` uses `FileProvider.getUriForFile` + explicit `FLAG_GRANT_READ_URI_PERMISSION` on `ACTION_SEND` — explicit grant, so compliant; `BackupManager.java:100` uses `ACTION_OPEN_DOCUMENT` (system grants) | `FileHelper.java` | **Low** — grant is explicit | none | Manual share/export on API 35 |
| IMP-47 | 17-all | Per-app Keystore limits | A17-all §Security | **No** | `grep -rn -E 'KeyStore\|KeyGenParameterSpec\|CryptoObject' app/src/main` → 0 hits (`BiometricPrompt` without `CryptoObject`) | — | Low | none | — |
| IMP-48 | 17-all | Block cross-profile loopback traffic | A17-all §Security | **No** | no network (IMP-12) | — | Low | none | — |
| IMP-49 | 17-all | Restoring default IME visibility after rotation | A17-all §UI | **Needs runtime check** | Edit screens use `TextInputEditText` fields (`medicine/EditMedicineFragment`, dialogs) | edit fragments | Low — cosmetic | none | Manual rotate on edit screen (API 35) |
| IMP-50 | 17-all | Touchpads deliver relative events during pointer capture | A17-all §Input | **No** | `grep -rn -E 'requestPointerCapture\|onCapturedPointerEvent' app/src/main` → 0 hits | — | Low | none | — |
| IMP-51 | 17-all | Autonomous re-pairing for Bluetooth bond losses | A17-all §Connectivity | **No** | IMP-07 | — | Low | none | — |
| IMP-52 | app risk | Exact alarms & reminder scheduling (`SCHEDULE_EXACT_ALARM` not pre-granted since targetSdk 33; must keep working on 37) | A16/A17 (no new change; re-verify) | **Needs runtime check** | `RescheduleWork.java:106-109` `canScheduleExactAlarms()` → `setExactAndAllowWhileIdle` else `setAndAllowWhileIdle`; `PreferencesFragment.java:114-115,186-187` prompts for the permission; `AndroidManifest.xml:8-10` declares `SCHEDULE_EXACT_ALARM` | `RescheduleWork.java`, `PreferencesFragment.java` | **High** (core function) — if alarm scheduling regresses, reminders are missed | none expected; add JVM tests for the `RescheduleWork` scheduling decision (exact vs inexact, permission-denied path) | `RescheduleWorkUnitTest` (new); existing `ReminderSchedulerUnitTest`; API 35 device flow: reminder 1–2 min ahead fires |
| IMP-53 | app risk | Boot / package-replaced autostart receiver | — | **Needs runtime check** | `Autostart.java` handles `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED` (`AndroidManifest.xml:56-63`, `exported=false`) | `Autostart.java` | Medium — reminders not restored after reboot/update | none expected | new JVM test for `Autostart` enqueueing the reschedule; API 35 `adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.futsch1.medtimer` |
| IMP-54 | app risk | Biometric lock | — | **No** | `Biometrics.kt` uses `androidx.biometric.BiometricPrompt` (1.1.0) without `CryptoObject` | — | Low | none | existing manual |
| IMP-55 | toolchain | AGP 9 built-in Kotlin / `kotlinOptions` removal / third-party plugin compatibility | AGP 9.0.0 notes | **Yes** | `build.gradle.kts:6` `org.jetbrains.kotlin.android 2.1.10`; `app/build.gradle.kts` `kotlinOptions { jvmTarget = "17" }` and plugins `com.github.triplet.play 3.12.1`, `org.sonarqube 5.1.0.4882`, `tech.apter…robolectric-extension-gradle-plugin 0.9.0` | build scripts | Medium — build breaks until migrated | §1 table; disable blocking non-essential plugins on this branch | `./gradlew assembleDebug testDebugUnitTest lintDebug` green |

## 4. Not affected — with evidence

IMP-03, 04, 06–09, 11–13, 16–19, 21–25, 28–33, 35–42, 44, 45, 47, 48, 50, 51, 54: each row above quotes the `grep -rn` that returned 0 hits in `app/src/main` (or the manifest line that removes the capability). In short: **no network, no native code, no Bluetooth, no SMS, no Keystore, no Contacts, no MediaStore, no periodic alarms/jobs, no reflective writes**.

## 5. Test plan

1. JVM (JUnit 5 + Robolectric 4.17, `@Config(sdk=37)` where the extension allows): existing 56 tests must stay green; new tests for `RescheduleWork` (exact vs inexact alarm decision), `ReminderScheduler`/`StandardScheduling` edge cases, `JSONBackup` reflective validation (IMP-28), `Autostart` (IMP-53), `WidgetImpl` line building (IMP-26).
2. Instrumented on **API 35** emulator (`connectedDebugAndroidTest`): existing suite + `Android17BehaviorTest` (IMP-01 insets, IMP-02/27 back). Numbers recorded in `docs/upgrade/test-evidence.md`.
3. **API 37**: local AVD is install-only (known SurfaceFlinger crash-loop on this VM) → `adb install` evidence only; UI/instrumented evidence via GitHub Actions `.github/workflows/test.yml` on `target_api_level: 37` with a screen-recording artifact.
4. Manual device flow on API 35 (recorded): create *Medicine A 500 mg*, reminder +1–2 min, notification appears, tap → overview.
5. Coverage: `jacocoUnitTestReport` before/after (line + branch, overall and per touched class).

## 6. Rollback plan

- All changes are on branch `devin/ee-32-android-17-upgrade`; nothing is merged by automation. Rollback = do not merge, or `git revert` the PR merge commit.
- Commits are ordered (toolchain → deps → compileSdk → targetSdk → per-IMP fixes) so a partial rollback is possible: reverting the `targetSdk 37` commit alone restores Android 15 runtime behavior while keeping the toolchain.
- Data: no Room schema change (database version unchanged), so downgrading the APK does not require data migration.
- If `targetSdk 37` cannot be made green within the time box, the fallback is `targetSdk 36` with `compileSdk 37` (documented as an open item).
