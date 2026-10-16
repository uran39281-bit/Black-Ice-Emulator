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

# Validate the exported bytes independently of the Android media stack.
ACCEPTANCE_MP4=BC-1/app/build/reports/androidTests/qa/acceptance-1080p.mp4
ffmpeg -v error -xerror -i "$ACCEPTANCE_MP4" -f null -
ffprobe -v error -show_streams -show_format -of json "$ACCEPTANCE_MP4" > BC-1/app/build/reports/androidTests/qa/acceptance-ffprobe.json
python3 - <<'PYVERIFY'
import json
from pathlib import Path
report=json.loads(Path('BC-1/app/build/reports/androidTests/qa/acceptance-ffprobe.json').read_text())
video=next(s for s in report['streams'] if s['codec_type']=='video')
audio=next(s for s in report['streams'] if s['codec_type']=='audio')
assert (video['codec_name'],video['width'],video['height'])==('h264',1920,1080), video
assert audio['codec_name']=='aac', audio
assert 3 < float(report['format']['duration']) < 5, report['format']
print('Independent MP4 check passed: H.264 1920x1080 + AAC; decoded without errors')
PYVERIFY
