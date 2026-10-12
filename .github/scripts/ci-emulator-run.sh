#!/usr/bin/env bash
# Post-boot part of the API 37 emulator job. reactivecircus/android-emulator-runner runs
# every `script:` line in its own shell, so everything that shares state lives here.
set -u
API="$1"
EXIT_CODE=0

./gradlew JacocoDebugCodeCoverage || EXIT_CODE=$?
adb pull /sdcard/googletest/test_outputfiles app/build/outputs/androidTest-results/ || true

# AGP's connected test task reported BUILD SUCCESSFUL although the device runner never
# installed the APK (PR #8 runs 38295620801 / 38299027059), so require a real report.
REPORT=$(ls app/build/outputs/androidTest-results/connected/debug/TEST-*.xml 2>/dev/null | head -1)
if [ -z "$REPORT" ]; then
    echo "No connected test report produced"
    EXIT_CODE=1
else
    TESTS=$(grep -o 'tests="[0-9]*"' "$REPORT" | head -1 | grep -o '[0-9]*')
    FAILS=$(grep -o 'failures="[0-9]*"' "$REPORT" | head -1 | grep -o '[0-9]*')
    ERRS=$(grep -o 'errors="[0-9]*"' "$REPORT" | head -1 | grep -o '[0-9]*')
    echo "connected tests=${TESTS:-0} failures=${FAILS:-?} errors=${ERRS:-?} ($REPORT)"
    if [ "${TESTS:-0}" -eq 0 ] || [ "${FAILS:-1}" -ne 0 ] || [ "${ERRS:-1}" -ne 0 ]; then
        EXIT_CODE=1
    fi
fi

INSTALLED=0
for i in 1 2 3 4 5 6; do
    if adb shell service check package | grep -q found && adb install -g app/build/outputs/apk/debug/MedTimer-debug.apk; then
        INSTALLED=1
        break
    fi
    echo "package service not ready / install failed, retry $i"
    sleep 15
done
if [ "$INSTALLED" -ne 1 ]; then
    echo "APK install failed after retries"
    exit 1
fi

adb shell "screenrecord --time-limit 25 /sdcard/app-launch-api${API}.mp4" &
sleep 2
adb shell am start -W -n com.futsch1.medtimer/.MainActivity
sleep 8
adb exec-out screencap -p > "app/build/outputs/app-launch-api${API}.png"
wait
adb pull "/sdcard/app-launch-api${API}.mp4" app/build/outputs/ || true
adb shell monkey -p com.futsch1.medtimer -v 5000 --throttle 10 || {
    echo "monkey failed"
    [ "$EXIT_CODE" -eq 0 ] && EXIT_CODE=1
}
exit $EXIT_CODE
