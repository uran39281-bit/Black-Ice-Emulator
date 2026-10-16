#!/usr/bin/env bash
set -euo pipefail
bash .github/scripts/bc1-media-fixtures.sh
(
  cd BC-1
  gradle --no-daemon -Pbc1SideBySide assembleDebug assembleDebugAndroidTest
)
mkdir -p BC-1/app/build/reports/androidTests
adb install -r BC-1/app/build/outputs/apk/debug/app-debug.apk
adb install -r BC-1/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c
adb shell am instrument -w -r com.blackice.bc1.preview.test/androidx.test.runner.AndroidJUnitRunner | tee BC-1/app/build/reports/androidTests/instrumentation.txt
adb pull /sdcard/Android/data/com.blackice.bc1.preview/files/qa BC-1/app/build/reports/androidTests/ || true
adb logcat -d > BC-1/app/build/reports/androidTests/logcat.txt
if rg 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' BC-1/app/build/reports/androidTests/instrumentation.txt; then exit 1; fi
rg '^OK \([0-9]+ tests?\)' BC-1/app/build/reports/androidTests/instrumentation.txt
