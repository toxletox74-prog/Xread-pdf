#!/bin/bash
# Lance l'app sur l'émulateur et remonte logcat + arbre d'UI en annotations GitHub
APK=app/build/outputs/apk/debug/app-debug.apk
adb install -r "$APK"
adb logcat -c
adb shell am start -W -n com.csa.xreadpdf/.MainActivity
sleep 12
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
adb logcat -d > logcat.txt
enc() { python3 -c 'import sys; s=sys.stdin.read()[:60000]; print(s.replace("%","%25").replace("\r","").replace("\n","%0A"))'; }
echo "::warning title=CRASH::$(grep -A40 -E 'FATAL EXCEPTION|AndroidRuntime' logcat.txt | head -80 | enc)"
echo "::warning title=APPLOG::$(grep -iE 'xreadpdf|compose|System.err' logcat.txt | grep -vE 'GoogleApiManager|chatty' | head -80 | enc)"
echo "::warning title=UI::$(python3 - <<'PY' | enc
import re
x=open('ui.xml').read() if __import__('os').path.exists('ui.xml') else ''
print('pkg:', set(re.findall(r'package="([^"]*)"', x)))
for m in re.finditer(r'<node[^>]*>', x):
    n=m.group(0)
    t=re.search(r' text="([^"]*)"',n).group(1); d=re.search(r'content-desc="([^"]*)"',n).group(1); b=re.search(r'bounds="([^"]*)"',n).group(1)
    if t or d: print(repr(t), repr(d), b)
PY
)"
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | head -3 | while read l; do echo "::warning title=ACT::$l"; done
