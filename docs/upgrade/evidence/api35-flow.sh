#!/bin/bash
# API 35 evidence flow for EE-32 (synthetic data only), robust variant: every tap targets a node found in the live UI dump.
set -u
E=~/ee32/evidence; P=com.futsch1.medtimer
rm -f $E/*.png $E/*.mp4 $E/flow.log
log(){ echo "$(date -u +%T) $*" | tee -a $E/flow.log; }
dump(){ adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1; }
field(){ dump; python3 -c "
import xml.etree.ElementTree as ET;r=ET.parse('/tmp/ui.xml').getroot();print(''.join(n.get('text') for n in r.iter('node') if (n.get('resource-id') or '').endswith('$1')))"; }
# tapnode <attr> <value> : wait up to 12s for a node with attr==value, tap its center
tapnode(){ for i in $(seq 1 12); do dump; C=$(python3 - "$1" "$2" <<'PY'
import sys,re,xml.etree.ElementTree as ET
a,v=sys.argv[1],sys.argv[2]
try: r=ET.parse('/tmp/ui.xml').getroot()
except Exception: sys.exit(0)
for n in r.iter('node'):
    if n.get(a)==v:
        x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds'))); print((x1+x2)//2,(y1+y2)//2); break
PY
); if [ -n "$C" ]; then adb shell input tap $C; log "tap $2 @ $C"; return 0; fi; sleep 1; done; log "NOT FOUND: $2"; return 1; }
adb shell input keyevent KEYCODE_BACK; adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell pm clear $P >/dev/null
adb shell pm grant $P android.permission.POST_NOTIFICATIONS
adb shell appops set $P SCHEDULE_EXACT_ALARM allow
log "device sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r') time=$(adb shell date +%H:%M:%S | tr -d '\r') app $(adb shell dumpsys package $P | grep -o 'targetSdk=[0-9]*' | head -1) versionName=$(adb shell dumpsys package $P | grep -o 'versionName=[0-9.]*' | head -1)"
adb shell "screenrecord --time-limit 170 /sdcard/demo.mp4" & REC=$!
sleep 2
log "am start: $(adb shell am start -W -n $P/.MainActivity | grep -E 'Status|TotalTime' | tr '\n' ' ')"
sleep 3; adb exec-out screencap -p > $E/api35-01-overview-empty.png
tapnode content-desc Medicine; sleep 1
tapnode resource-id com.futsch1.medtimer:id/addMedicine
tapnode resource-id com.futsch1.medtimer:id/medicineName; adb shell input text "Medicine%sA%s500%smg"; sleep 1
tapnode resource-id android:id/button1; sleep 2
adb exec-out screencap -p > $E/api35-02-medicine-created.png
tapnode resource-id com.futsch1.medtimer:id/addReminder; sleep 2.5
tapnode resource-id com.futsch1.medtimer:id/editAmount; sleep 0.7; adb shell input text "1"; sleep 0.5
NOW=$(adb shell date +%s | tr -d '\r'); T=$((NOW+125))
log "interval reminder: 2 minutes from now (device clock $(adb shell date +%H:%M:%S | tr -d '\r'))"
tapnode resource-id com.futsch1.medtimer:id/intervalBased; sleep 1.5
# select the unit first: switching unit converts the current value (12 h -> 720 min)
tapnode resource-id com.futsch1.medtimer:id/intervalMinutes; sleep 0.7
tapnode resource-id com.futsch1.medtimer:id/editIntervalTime; sleep 0.5
for try in 1 2 3; do
  adb shell input keyevent KEYCODE_MOVE_END; sleep 0.3; adb shell input keyevent KEYCODE_DEL; sleep 0.3; adb shell input keyevent KEYCODE_DEL; sleep 0.3; adb shell input keyevent KEYCODE_DEL; sleep 0.3
  adb shell input text "2"; sleep 0.6; GOT=$(field editIntervalTime); log "interval field: '$GOT'"; [ "$GOT" = "2" ] && break
done
adb exec-out screencap -p > $E/api35-03-reminder-dialog-interval.png
dump; log "dialog: interval=$(python3 -c "import xml.etree.ElementTree as ET;r=ET.parse('/tmp/ui.xml').getroot();print([n.get('text') for n in r.iter('node') if (n.get('resource-id') or '').endswith('editIntervalTime')])") minutesChecked=$(python3 -c "import xml.etree.ElementTree as ET;r=ET.parse('/tmp/ui.xml').getroot();print([n.get('checked') for n in r.iter('node') if (n.get('resource-id') or '').endswith('intervalMinutes')])")"
tapnode resource-id com.futsch1.medtimer:id/createReminder; sleep 2
adb exec-out screencap -p > $E/api35-05-medicine-with-reminder.png
tapnode content-desc Overview; sleep 2
adb exec-out screencap -p > $E/api35-06-overview-next-reminder.png
log "alarm: $(adb shell dumpsys alarm | grep -i -A3 'medtimer' | grep -m1 -E 'RTC_WAKEUP|when=' | sed 's/^ *//' | cut -c1-160)"
while [ $(adb shell date +%s | tr -d '\r') -lt $((T+3)) ]; do sleep 2; done
for i in $(seq 1 12); do adb shell dumpsys notification --noredact 2>/dev/null | grep -q "pkg=$P" && break; sleep 3; done
log "notification: $(adb shell dumpsys notification --noredact | grep -A8 "pkg=$P" | grep -m2 -E 'android.title|android.text' | sed 's/^ *//' | tr '\n' ' ' | cut -c1-220)"
adb exec-out screencap -p > $E/api35-07-notification-statusbar.png
adb shell cmd statusbar expand-notifications; sleep 2
adb exec-out screencap -p > $E/api35-08-notification-shade.png
adb shell cmd statusbar collapse; sleep 1.5
adb exec-out screencap -p > $E/api35-09-overview-after-reminder.png
wait $REC; sleep 1
adb pull /sdcard/demo.mp4 $E/api35-demo.mp4 | tail -1 | tee -a $E/flow.log
log "done"
