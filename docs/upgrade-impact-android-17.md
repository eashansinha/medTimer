# Change impact assessment: MedTimer Android 15 (API 35) → Android 17 (API 37)

**Status: DRAFT for human review.** This document is an engineering input; it does not claim compliance with IEC 62304, FDA regulations or any other standard.

- Ticket: [EE-29](https://cog-gtm.atlassian.net/browse/EE-29) — "Upgrade MedTimer from Android 15 (API 35) to Android 17 (API 37)"
- Baseline: `main` = v1.16.5, `compileSdk 35`, `targetSdk 35`, `minSdk 28`, AGP 8.9.0, Gradle 8.11.1, Kotlin 2.1.10 (see `docs/upgrade/baseline.md`)
- Target: `compileSdk 37`, `targetSdk 37`, `minSdk 28` (unchanged)
- Written and committed **before** any code change on branch `devin/ee-29-android-17-upgrade`.
- Sources read on 2026-10-12:
  - B16T = https://developer.android.com/about/versions/16/behavior-changes-16
  - B16A = https://developer.android.com/about/versions/16/behavior-changes-all
  - B17T = https://developer.android.com/about/versions/17/behavior-changes-17
  - B17A = https://developer.android.com/about/versions/17/behavior-changes-all
  - AGP = https://developer.android.com/build/releases/about-agp and https://developer.android.com/build/releases/gradle-plugin
- Code evidence was collected with `grep -rn` over `app/src/main` (Java/Kotlin/XML). "No usage" means the pattern in the *Evidence* column returned nothing. Line numbers refer to `main` at commit `8fde0b91`.

## 1. Behavior-change table

Legend — *Applies*: Yes / No / Needs runtime check. *Risk*: High / Medium / Low.

### 1.1 Android 16 — apps targeting API 36+ (B16T)

| ID | Android | Change | Source | Applies | Code evidence | Affected files | Risk | Risk reason | Planned fix | Verification |
|---|---|---|---|---|---|---|---|---|---|---|
| IMP-01 | 16 | Edge-to-edge: `R.attr#windowOptOutEdgeToEdgeEnforcement` opt-out is ignored; apps are always edge-to-edge | B16T | **Yes** | No opt-out declared (`grep windowOptOutEdgeToEdgeEnforcement` → none). Root layout relies on `android:fitsSystemWindows="true"` (`app/src/main/res/layout/activity_main.xml:7`, a `ConstraintLayout`); theme `Theme.MedTimer` extends `Theme.Material3.DynamicColors.Light.NoActionBar` (`res/values/themes.xml:3`); `statusBarColor` transparent only in unused `Theme.MedTimer2` (`themes.xml:7`). No `WindowInsets` handling in Java/Kotlin. | `MainActivity.java`, `res/layout/activity_main.xml`, `res/values/themes.xml`, fragment layouts under `res/layout/` | Medium | App already runs edge-to-edge on API 35 via Material3 + `fitsSystemWindows`; the risk is content under the system bars / 3-button nav bar once the forced mode applies to API 36+ targets. | Apply `ViewCompat.setOnApplyWindowInsetsListener` on the activity root to pad for system bars + display cutout, so layout does not depend on the deprecated `fitsSystemWindows` path. | Manual: API 35 emulator launch screenshot (`docs/upgrade/evidence/`); CI on API 37 (`.github/workflows/test.yml`) screen recording; `BasicUITest` (instrumented) |
| IMP-02 | 16 | Predictive back enabled by default: `onBackPressed()`/`KEYCODE_BACK` are not called when back-invoked callbacks are used | B16T | **Yes** (already prepared) | `AndroidManifest.xml:24` `android:enableOnBackInvokedCallback="true"` (already opted in); back handled via AndroidX `OnBackPressedCallback` (`MainActivity.java:103-112`), no `onBackPressed()` override or `KEYCODE_BACK` handling (`grep onBackPressed\|KEYCODE_BACK` → only the dispatcher call). Navigation uses `androidx.navigation` 2.8.9. | `MainActivity.java`, `AndroidManifest.xml` | Low | Already migrated to the AndroidX dispatcher model; the custom callback re-posts the event after 20 ms, which is compatible with predictive back. | None required. Verify that the delayed callback does not break the predictive-back animation. | Manual: back navigation in API 35 recording; `BasicUITest` navigation tests |
| IMP-03 | 16 | Elegant font APIs deprecated/disabled (`elegantTextHeight` ignored) | B16T | No | `grep elegantTextHeight` → none | — | Low | Not used. | None | — |
| IMP-04 | 16 | Fixed-rate `scheduleAtFixedRate` replays at most one missed execution | B16T | No | `grep scheduleAtFixedRate\|ScheduledExecutor\|Timer(` → none; reminder timing uses `AlarmManager` + WorkManager (`RescheduleWork.java:106-109`) | — | Low | Not used. | None | — |
| IMP-05 | 16 | Large screens (sw≥600dp): orientation, resizability and aspect-ratio restrictions ignored (opt-out via `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` only until API 36) | B16T | No restriction declared → **Needs runtime check** of layouts | `grep screenOrientation\|resizeableActivity\|AspectRatio\|setRequestedOrientation\|PROPERTY_COMPAT` → none; activities already resizable by default | `res/layout/*` (fragments) | Low | No restrictions to lose; UI must simply render in landscape/tablet windows, which it already must on API 35. | None | Manual: rotate API 35 emulator (`adb shell settings put system user_rotation 1`) during evidence run |
| IMP-06 | 16 | Health & fitness: `BODY_SENSORS` → granular Health Connect permissions | B16T | No | `grep BODY_SENSORS\|HealthConnect` → none | — | Low | Not used. | None | — |
| IMP-07 | 16 | Bluetooth bond-loss handling (`ACTION_KEY_MISSING`, `ACTION_ENCRYPTION_CHANGE`) | B16T | No | `grep Bluetooth` → none | — | Low | Not used. | None | — |
| IMP-08 | 16 | CompanionDeviceManager `removeBond()` / discovery changes | B16T | No | `grep CompanionDevice` → none | — | Low | Not used. | None | — |
| IMP-09 | 16 | `MediaStore.getVersion()` per-app value | B16T | No | `grep MediaStore` → none | — | Low | Not used. | None | — |
| IMP-10 | 16 | Safer Intents (`intentMatchingFlags`, explicit intents must match filters) — opt-in | B16T | No (opt-in) | All receivers `android:exported="false"` with explicit intents (`AndroidManifest.xml:57-100`); `PendingIntentBuilder.java:38`, `Notifications.java:85-160` use explicit component intents | — | Low | Opt-in only; explicit intents already match. | None | `NotificationTest` (instrumented) |
| IMP-11 | 16 | GPU syscall filtering (Mali) | B16T | No | No native/GPU code (`grep System.load\|native ` → none; 0 `.so` in `MedTimer-debug.apk`) | — | Low | Pure JVM app. | None | — |
| IMP-12 | 16 | Local network permission (opt-in phase in Android 16) | B16T | No | `INTERNET` and `ACCESS_NETWORK_STATE` are *removed* from the merged manifest (`AndroidManifest.xml:12-17`, `tools:node="remove"`); `grep Socket\|HttpURLConnection\|OkHttp\|WebView\|NsdManager` → none | — | Low | App has no network access. | None | Manual: `aapt dump permissions` on upgraded APK shows no INTERNET |
| IMP-13 | 16 | App-owned photos pre-selected in photo picker | B16T | No | `grep PickVisualMedia\|ACTION_PICK\|READ_MEDIA` → none | — | Low | Not used. | None | — |

### 1.2 Android 16 — all apps (B16A)

| ID | Android | Change | Source | Applies | Code evidence | Affected files | Risk | Risk reason | Planned fix | Verification |
|---|---|---|---|---|---|---|---|---|---|---|
| IMP-14 | 16 | JobScheduler quota optimisations (affects WorkManager-backed jobs; jobs may be deferred when the app's standby bucket quota is exhausted) | B16A | **Yes** | All reminder processing runs as WorkManager `OneTimeWorkRequest`s: `Autostart.java:52-60` (boot/package-replaced), `RescheduleWork.java`, `ReminderProcessor.java` → `WorkManagerAccess.java:18-38` (custom `WorkManager.initialize`, default initializer removed in manifest). Alarm delivery itself is `AlarmManager` (`RescheduleWork.java:106-109`), so the user-visible notification is not quota-bound, but the follow-up work is. `work-runtime 2.10.0` (`app/build.gradle.kts:126`). | `Autostart.java`, `RescheduleWork.java`, `ReminderProcessor.java`, `WorkManagerAccess.java`, `app/build.gradle.kts` | Medium | A delayed `ReminderWork` after reboot could delay the first notification; Google recommends current WorkManager for quota handling. | Bump `androidx.work:work-runtime` to the latest stable (lint baseline already reports 2.12.0 available) as part of the behavior-change commits; no code change. | `ReminderWorkUnitTest`, `RepeatReminderWorkUnitTest`, `SnoozeWorkUnitTest` (JVM); manual: API 35 notification in recording |
| IMP-15 | 16 | Abandoned jobs get stop reason `STOP_REASON_TIMEOUT_ABANDONED` (WorkManager not affected per docs) | B16A | No | WorkManager only, no direct `JobScheduler` (`grep JobScheduler\|JobService` → none) | — | Low | Docs state WorkManager is not impacted. | None | — |
| IMP-16 | 16 | Ordered broadcast priority no longer global across processes | B16A | No | `grep android:priority\|setPriority(` → only `NotificationCompat.setPriority` (`Notifications.java:55,177`), no broadcast priorities | — | Low | Not used. | None | — |
| IMP-17 | 16 | ART internal changes (non-SDK internals may break) | B16A | **Needs runtime check** | No `setAccessible`, no `Class.forName` (`grep` → none). Reflection: `JSONBackup.java:81-88` reads *own* POJO fields via `getDeclaredFields()`+`Field.get`; `OptionsMenu.java:96` calls `getDeclaredMethod("setOptionalIconsVisible")` on AppCompat `MenuBuilder` (library class, not platform SDK). Gson reflects over own entities (`Converters.java`, `DatabaseEntityEditFragment.kt:130`). | `database/JSONBackup.java`, `OptionsMenu.java` | Low | Only app/library classes are reflected, no hidden platform APIs. | None | `JSONMedicineBackupUnitTest`, `JSONReminderEventBackupUnitTest` (JVM); options menu visible in API 35 screenshots |
| IMP-18 | 16 | 16 KB page-size compatibility mode | B16A | No | 0 native libraries in APK (`unzip -l MedTimer-debug.apk \| grep -c '\.so$'` → 0) | — | Low | No native code. | None | — |
| IMP-19 | 16 | `announceForAccessibility` / `TYPE_ANNOUNCEMENT` deprecated | B16A | No | `grep announceForAccessibility\|TYPE_ANNOUNCEMENT` → none | — | Low | Not used. | None | — |
| IMP-20 | 16 | Predictive back for 3-button navigation | B16A | Yes (see IMP-02) | Same evidence as IMP-02 | `MainActivity.java` | Low | Same as IMP-02. | None | Manual back press in recording (3-button nav on emulator) |
| IMP-21 | 16 | Automatic themed app icons (monochrome layer applied by launcher) | B16A | **Yes** (already prepared) | Both adaptive icons declare `<monochrome>` (`res/mipmap-anydpi/logo.xml:5`, `logo_round.xml:5`) | — | Low | Monochrome layer exists. | None | Manual: launcher screenshot on API 35 |
| IMP-22 | 16 | Virtual device owner overrides | B16A | No | No virtual-device APIs (`grep VirtualDevice` → none) | — | Low | Not used. | None | — |
| IMP-23 | 16 | Intent redirection hardening (nested intents in extras) | B16A | **Needs runtime check** | Notification actions use explicit broadcast/activity `PendingIntent`s with `FLAG_IMMUTABLE` (`Notifications.java:85-160`, `PendingIntentBuilder.java:38`, `WidgetImpl.kt:58-62`); no `Intent` objects passed as extras (`grep putExtra.*Intent` → none) | — | Low | No nested intents. | None | `NotificationTest`, widget tap in evidence run |
| IMP-24 | 16 | CompanionDeviceManager discovery timeout | B16A | No | See IMP-08 | — | Low | Not used. | None | — |

### 1.3 Android 17 — apps targeting API 37+ (B17T)

| ID | Android | Change | Source | Applies | Code evidence | Affected files | Risk | Risk reason | Planned fix | Verification |
|---|---|---|---|---|---|---|---|---|---|---|
| IMP-25 | 17 | RemoteViews (widgets/notification custom views) memory limit `1.5 × width × height × 4` for Bitmaps + Icons; exceeding → fatal `IllegalArgumentException` | B17T | **Needs runtime check** | Widgets build `RemoteViews` from text-only layouts (`widgets/WidgetImpl.kt:30,38,46-50`; `res/layout/next_reminders_widget*.xml`, `latest_reminders_widget*.xml`); `grep setImageViewBitmap\|setImageViewIcon` → none. Notifications use no custom content view (`grep setCustomContentView\|DecoratedCustomViewStyle` → none). | `widgets/WidgetImpl.kt`, widget layouts | Low | No bitmaps/icons placed in RemoteViews; only drawables referenced by resource id. | None; add a JVM test asserting widget `RemoteViews` carry no bitmap payload is not feasible without device → covered by CI on API 37. | CI API 37: widget host not exercised; manual: add widget on API 35 (`adb shell am start` not applicable) — recorded as open item if not covered |
| IMP-26 | 17 | Lock-free `MessageQueue` implementation (breaks reflection on `MessageQueue` internals) | B17T | No | `grep MessageQueue` → none; only `Handler(Looper)` usage (`OptionsMenu.java:70,146,166,197`, `OverviewFragment.java:97,140`, `MainActivity.java:107`, `DatabaseEntityEditFragment.kt:87`) | — | Low | Public Handler API only. | None | Existing UI tests |
| IMP-27 | 17 | `static final` fields unmodifiable via reflection (`IllegalAccessException`) / JNI crash | B17T | No (read-only reflection) → **Needs runtime check** | `JSONBackup.java:81-88` only *reads* fields (`f.get(pojo)`); Gson sets *instance* fields of own entities (excludes static by default); `grep Field.set\|setAccessible\|Modifier.` → none. No JNI. | `database/JSONBackup.java`, `database/Converters.java` | Low | No writes to static final fields. | None | `JSONMedicineBackupUnitTest`, `JSONReminderEventBackupUnitTest`, `JSONBackupFuzzTest` (JVM) |
| IMP-28 | 17 | Accessibility text-change types for IME typing (automatic for standard `TextView`) | B17T | No action | Standard `TextInputEditText`/`TextView` only; `grep InputConnection` → none | — | Low | Platform handles. | None | — |
| IMP-29 | 17 | Encrypted Client Hello enabled for TLS | B17T | No | No network (IMP-12) | — | Low | No TLS connections. | None | — |
| IMP-30 | 17 | `ACCESS_LOCAL_NETWORK` runtime permission mandatory | B17T | No | No network (IMP-12): `INTERNET` removed `AndroidManifest.xml:12-17` | — | Low | No LAN access. | None | `aapt dump permissions` on final APK |
| IMP-31 | 17 | Passwords hidden on physical keyboards | B17T | No | `grep textPassword\|numberPassword` → none; lock uses system `BiometricPrompt` (`Biometrics.kt`) | — | Low | No password fields. | None | — |
| IMP-32 | 17 | SMS OTP protection for standard SMS (3 h delay) | B17T | No | `grep SMS\|Telephony` → none | — | Low | Not used. | None | — |
| IMP-33 | 17 | Activity security: BAL hardening extended to `IntentSender`, `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` deprecated | B17T | **Needs runtime check** | Activity `PendingIntent`s are only fired by user taps on notifications/widgets (`Notifications.java:85,147,157`, `WidgetImpl.kt:58`); no `ActivityOptions`/`MODE_BACKGROUND_ACTIVITY_START*` (`grep` → none); `startActivity` only from foreground UI (`MainActivity.java:63`, `OptionsMenu.java:122,187`, `PreferencesFragment.java:50,141,170`, `FileHelper.java:79`) | `reminders/Notifications.java` | Low | All activity starts are user-initiated from a visible context or a notification tap (exempt). | None | `NotificationTest` (instrumented), manual notification tap in recording |
| IMP-34 | 17 | Certificate Transparency enabled by default | B17T | No | No network (IMP-12) | — | Low | No TLS. | None | — |
| IMP-35 | 17 | Safer native DCL: `System.load()` requires read-only files | B17T | No | `grep System.load` → none; 0 `.so` | — | Low | No native code. | None | — |
| IMP-36 | 17 | Contacts Provider: PII columns restricted | B17T | No | `grep Contacts` → none | — | Low | Not used. | None | — |
| IMP-37 | 17 | Contacts Provider: strict SQL checks | B17T | No | As IMP-36 | — | Low | Not used. | None | — |
| IMP-38 | 17 | `setContentCaptureEnabled(false)` deprecated; use `FLAG_SECURE` | B17T | No (already compliant) | `grep setContentCaptureEnabled` → none; `MainActivity.java:48` already sets `FLAG_SECURE` when the "secure window" preference is on | — | Low | Already uses the recommended mechanism. | None | Manual: enable secure-window preference, `adb exec-out screencap` is black |
| IMP-39 | 17 | Background audio hardening: audio playback / focus / volume-change APIs from background require a while-in-use FGS, or exact-alarm permission + `USAGE_ALARM`; otherwise silently fail (volume) | B17T | **Yes** | `reminders/NotificationSoundManager.kt:15-16,40-48,76-84`: from `ReminderWork` (background WorkManager worker, no foreground service — `grep startForeground\|FOREGROUND_SERVICE` → none) the app calls `AudioManager.adjustStreamVolume(STREAM_RING, ADJUST_UNMUTE/ADJUST_MUTE)` and sets `ringerMode`. The app holds `SCHEDULE_EXACT_ALARM` but acts on `STREAM_RING`, not `USAGE_ALARM`. | `reminders/NotificationSoundManager.kt`, `reminders/ReminderWork.java` | High | The optional "play sound even when muted" behaviour may silently stop working on Android 17; the notification itself is still posted (notification sound is played by the system, not the app). | No code change that can be verified on this VM (API 37 emulator has no working UI). Document as known limitation/open item; add a JVM test pinning the current API-28+ mute/unmute logic so a later fix is testable. | Open item: verify on API 37 device/CI with the "override mute" preference; JVM test for `NotificationSoundManager` decision logic |
| IMP-40 | 17 | Large-screen orientation/resizability/aspect-ratio opt-out removed | B17T | No | As IMP-05 (no restrictions declared) | — | Low | Nothing to opt out of. | None | Manual rotate on API 35 |
| IMP-41 | 17 | `BluetoothSocket.read()` returns −1 on close | B17T | No | As IMP-07 | — | Low | Not used. | None | — |

### 1.4 Android 17 — all apps (B17A)

| ID | Android | Change | Source | Applies | Code evidence | Affected files | Risk | Risk reason | Planned fix | Verification |
|---|---|---|---|---|---|---|---|---|---|---|
| IMP-42 | 17 | App memory limits based on device RAM (`MemoryLimiter:AnonSwap` exit reason) | B17A | **Needs runtime check** | App is small (text/UI, Room DB); no bitmap caches (`grep Bitmap` → only AppIntro drawables via resources) | — | Low | Unlikely to approach limits; cannot measure without a working API 37 UI emulator. | None | CI API 37 monkey test (`adb shell monkey ... 5000`) completes without `MemoryLimiter` exit |
| IMP-43 | 17 | SMS OTP protection for WebOTP messages | B17A | No | As IMP-32 | — | Low | Not used. | None | — |
| IMP-44 | 17 | `usesCleartextTraffic` deprecation plan (future release) | B17A | **Yes** (future) | `AndroidManifest.xml:33` `android:usesCleartextTraffic="false"`; `minSdk 28 ≥ 24`; no network security config file (`grep network_security\|networkSecurityConfig` → none) | `AndroidManifest.xml` | Low | Deprecation only announced; attribute is harmless today and the app has no network. | None now; note for a future release (replace with `networkSecurityConfig` or drop since no network). | — |
| IMP-45 | 17 | Implicit URI grants for `ACTION_SEND`/`ACTION_SEND_MULTIPLE`/`ACTION_IMAGE_CAPTURE` will stop in Android 18 | B17A | No (already compliant) | `helpers/FileHelper.java:71-77`: `ACTION_SEND` intent already calls `addFlags(FLAG_GRANT_READ_URI_PERMISSION)` with a `FileProvider` URI (`AndroidManifest.xml` provider) | `helpers/FileHelper.java` | Low | Explicit grant already present. | None | `ExportBackupTest` (instrumented) |
| IMP-46 | 17 | Per-app Keystore key limit (50 000 keys for API 37+ apps) | B17A | No | `grep KeyStore\|KeyGenParameterSpec\|Cipher\|CryptoObject` → none; `androidx.biometric` `BiometricPrompt` used without `CryptoObject` (`Biometrics.kt`) | — | Low | App creates no Keystore keys. | None | — |
| IMP-47 | 17 | Cross-profile loopback traffic blocked | B17A | No | No network (IMP-12) | — | Low | Not used. | None | — |
| IMP-48 | 17 | IME visibility not restored after unhandled rotation | B17A | **Needs runtime check** | No `android:windowSoftInputMode` or `configChanges` on activities (`grep` → none); forms use `TextInputEditText` | `AndroidManifest.xml` | Low | Cosmetic: keyboard closes after rotation while editing a medicine; acceptable, no fix planned. | None (document) | Manual rotate while editing on API 35 (behaviour identical on 35, so only documents expectation) |
| IMP-49 | 17 | Touchpad relative events during pointer capture | B17A | No | `grep requestPointerCapture` → none | — | Low | Not used. | None | — |
| IMP-50 | 17 | Background audio hardening (all apps: silent failure of playback/volume APIs outside valid lifecycle) | B17A | **Yes** | As IMP-39 | `reminders/NotificationSoundManager.kt` | High | Same as IMP-39 (applies even before targetSdk 37 on Android 17 devices). | As IMP-39 | As IMP-39 |
| IMP-51 | 17 | Bluetooth autonomous re-pairing | B17A | No | As IMP-07 | — | Low | Not used. | None | — |

### 1.5 App-specific risk areas (cross-reference)

| Risk area | Relevant IMP rows | Evidence summary |
|---|---|---|
| Exact alarms / `SCHEDULE_EXACT_ALARM` | IMP-04, IMP-14, IMP-39 | `RescheduleWork.java:106-109,143-147` uses `setExactAndAllowWhileIdle` when `canScheduleExactAlarms()` else `setAndAllowWhileIdle`; `PreferencesFragment.java:112-125,185-190` drives `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`. No Android 16/17 change to `SCHEDULE_EXACT_ALARM` semantics on the four pages (Android 14 rules still apply). |
| Reminder scheduling / alarm time calculation | IMP-04, IMP-14 | `reminders/scheduling/*` pure JVM logic, no platform API; unaffected by the behavior changes but highest value for regression tests. |
| Notifications and actions | IMP-10, IMP-23, IMP-25, IMP-33 | `Notifications.java`, `NotificationAction.java`, `ReminderProcessor.java`; no custom views, explicit immutable PendingIntents. |
| App widgets (RemoteViews) | IMP-25 | `widgets/WidgetImpl.kt`; text-only layouts. |
| Boot / autostart receivers | IMP-14, IMP-16 | `Autostart.java:33-60`; `BOOT_COMPLETED` + `MY_PACKAGE_REPLACED`, enqueues WorkManager. |
| WorkManager | IMP-14, IMP-15 | `WorkManagerAccess.java`; one-time requests only. |
| Biometric lock | IMP-31, IMP-46 | `Biometrics.kt` uses `androidx.biometric` without crypto. |
| Backup / import reflection | IMP-17, IMP-27 | `JSONBackup.java:81-88` read-only reflection; Gson instance fields. |
| Edge-to-edge layouts | IMP-01 | `activity_main.xml:7`, `themes.xml`. |
| Predictive back | IMP-02, IMP-20 | manifest opt-in + AndroidX dispatcher. |
| Large-screen orientation | IMP-05, IMP-40 | nothing declared. |

## 2. Toolchain section

Verified against the AGP pages on 2026-10-12 (not from memory):

| Component | Baseline (`main`) | Target | Source / reason |
|---|---|---|---|
| Android Gradle Plugin | 8.9.0 | **9.4.0** (newest stable; "Maximum API level 37"; API 37.0 requires AGP ≥ 9.1.1 per the API-level table) | AGP about page |
| Gradle wrapper | 8.11.1 | **9.6.0** (minimum/default for AGP 9.4) | AGP compatibility table |
| JDK | 17 | 17 (AGP 9.4 requires JDK 17) | AGP about page; VM has OpenJDK 17.0.20.1 |
| Kotlin Gradle plugin | 2.1.10 (`org.jetbrains.kotlin.android`) | AGP 9 built-in Kotlin: `org.jetbrains.kotlin.android` plugin removed, `kotlinOptions {}` → `kotlin { compilerOptions {} }`; Kotlin version is the one bundled with AGP 9.4 (docs example 2.4.10). | AGP 9 migration notes in release notes |
| Build tools | default (35.0.0) | 37.0.0 explicitly (AGP 9.4 minimum 36.0.0; VM has 35.0.0 and 37.0.0 installed) | AGP about page |
| Room | 2.6.1 (plugin + runtime) | bump only if the Room Gradle plugin fails under Gradle 9 / built-in Kotlin (KSP vs kapt not used — annotationProcessor; check) | to be validated by build |
| Navigation Safe Args | 2.8.9 | bump to the version compatible with AGP 9 if plugin application fails | to be validated by build |
| Robolectric | 4.14.1 | bump to a release that knows SDK 36/37 (`compileSdk 37` with `isIncludeAndroidResources` requires a Robolectric that supports the new SDK, otherwise tests fall back/fail) | to be validated by `testDebugUnitTest` |
| JaCoCo | Gradle built-in | Gradle 9 default JaCoCo; verify `JacocoDebugCodeCoverage` class dirs (`intermediates/javac`, `tmp/kotlin-classes`) still resolve under built-in Kotlin | to be validated |
| Play publisher `com.github.triplet.play` 3.12.1 | applied | disable on this branch if incompatible with Gradle 9 / AGP 9 (no publishing is done by this work) | user instruction |
| SonarQube 5.1.0.4882 | applied | disable on this branch if incompatible with Gradle 9 | user instruction |
| junit5 robolectric extension 0.9.0 | applied | disable/bump if incompatible | user instruction |
| Core library desugaring | 2.1.5 | unchanged unless required | — |

Known AGP 9 migration items (from release notes): built-in Kotlin (`kotlin-android` plugin must not be applied), `kotlinOptions` removed, several deprecated DSL/Variant APIs removed, `android.enableR8.fullMode` default, Gradle 9 removes deprecated APIs used by older third-party plugins. Each deviation from the plan above will be recorded in the commit body and in this section's "Actual outcome" subsection, filled in after the toolchain commits.

### Actual outcome (filled during Step 3)

_To be completed._

## 3. Module / package impact map

Single Gradle module `:app` (namespace `com.futsch1.medtimer`).

| Package / file | Impacted by | Change expected |
|---|---|---|
| `com.futsch1.medtimer.MainActivity` | IMP-01, IMP-02 | insets handling; back callback verified |
| `res/layout/activity_main.xml`, `res/values/themes.xml` | IMP-01 | insets/`fitsSystemWindows` |
| `com.futsch1.medtimer.reminders.NotificationSoundManager` | IMP-39/50 | documented limitation + JVM test |
| `com.futsch1.medtimer.reminders.*Work`, `Autostart`, `WorkManagerAccess` | IMP-14 | WorkManager bump; tests |
| `com.futsch1.medtimer.reminders.scheduling.*` | none (JVM logic) | new regression tests only |
| `com.futsch1.medtimer.widgets.*` | IMP-25 | none expected |
| `com.futsch1.medtimer.database.JSONBackup`, `Converters` | IMP-17, IMP-27 | none expected |
| `com.futsch1.medtimer.helpers.FileHelper` | IMP-45 | none |
| `AndroidManifest.xml` | IMP-44 (future) | none now |
| `app/build.gradle.kts`, `build.gradle.kts`, `gradle/wrapper/*` | Toolchain | AGP/Gradle/Kotlin/compileSdk/targetSdk |
| `.github/workflows/test.yml` | Evidence | API 37 emulator + screen recording artifact |

## 4. Not affected, with evidence

| Change | Evidence |
|---|---|
| Local network permission (IMP-12/30), CT (IMP-34), ECH (IMP-29), cross-profile loopback (IMP-47) | `INTERNET` removed in `AndroidManifest.xml:12-17`; no network classes in code |
| SMS OTP (IMP-32/43) | no `SmsManager`/`Telephony` usage |
| Keystore limits (IMP-46) | no Keystore / `CryptoObject` usage |
| Lock-free MessageQueue (IMP-26) | no `MessageQueue` reference |
| Static final reflection (IMP-27) | only read-only reflection in `JSONBackup.java:81-88` |
| Native code changes (IMP-11/18/35) | 0 `.so` files in the debug APK |
| Health, Bluetooth, CDM, Contacts, MediaStore, photo picker, pointer capture (IMP-06..09, 13, 24, 36, 37, 41, 49, 51) | grep for the respective API names returns nothing |
| Elegant fonts (IMP-03), `scheduleAtFixedRate` (IMP-04), ordered broadcast priority (IMP-16), `announceForAccessibility` (IMP-19) | grep returns nothing |

## 5. Test plan

1. JVM: `./gradlew testDebugUnitTest` plus a unit-test-only JaCoCo report (`jacocoUnitTestReport` task added to `app/build.gradle.kts`), compared with `docs/upgrade/baseline.md`.
2. New JVM tests (Step 4), each able to fail: reminder scheduling / alarm time edge cases (`reminders/scheduling`), `RescheduleWork` alarm-type selection (exact vs inexact, IMP-14 area), snooze/repeat work, `NotificationSoundManager` decision logic (IMP-39), `Autostart` action filtering.
3. Lint: `./gradlew lintDebug` — baseline fails with 19 `GradleDependency` errors; result recorded, not suppressed.
4. Instrumented: `./gradlew connectedDebugAndroidTest` on the API 35 AVD (API 37 AVD has no working SurfaceFlinger on this VM — install evidence only).
5. Manual evidence on API 35 (`docs/upgrade/evidence/`): launch, grant notifications, create medicine + reminder, notification, overview, back navigation, rotation; `screenrecord` + screenshots.
6. CI (`.github/workflows/test.yml`) on API 37 `google_apis` x86_64 with launch screen recording uploaded as artifact — reported with the real run URL.

## 6. Rollback plan

- All changes land in one PR on `devin/ee-29-android-17-upgrade`; rollback = revert the merge commit (or do not merge).
- Toolchain and SDK bumps are separate commits (wrapper/AGP → dependencies → compileSdk → targetSdk → IMP fixes), so a partial rollback (e.g. keep AGP 9 but revert `targetSdk` to 35/36) is possible by reverting the `targetSdk` commit alone; no database schema or data-format change is involved, so downgrading the app build does not affect user data.
- No Play publishing, signing or release configuration is touched.
