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
adb logcat -d > BC-1/app/build/reports/androidTests/logcat.txt
mkdir -p BC-1/app/build/reports/androidTests/qa
adb exec-out run-as com.blackice.bc1.preview tar -C files/qa -cf - . > BC-1/app/build/reports/androidTests/qa.tar
tar -xf BC-1/app/build/reports/androidTests/qa.tar -C BC-1/app/build/reports/androidTests/qa
if rg 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' BC-1/app/build/reports/androidTests/instrumentation.txt; then exit 1; fi
rg '^OK \([0-9]+ tests?\)' BC-1/app/build/reports/androidTests/instrumentation.txt
