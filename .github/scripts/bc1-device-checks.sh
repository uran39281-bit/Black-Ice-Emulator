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
# Artifacts are not always retrievable, so surface export failures directly in the job log.
rg -n -A40 'BC1Export\b|MuxerException|Muxer error' BC-1/app/build/reports/androidTests/logcat.txt | rg -v 'WindowManager|SplashScreen|TopTaskTracker|OptProp' | head -150 || true
mkdir -p BC-1/app/build/reports/androidTests/qa
adb exec-out run-as com.blackice.bc1.preview tar -C files/qa -cf - . > BC-1/app/build/reports/androidTests/qa.tar
tar -xf BC-1/app/build/reports/androidTests/qa.tar -C BC-1/app/build/reports/androidTests/qa
# Repeat the instrumentation failure summary last, so it is visible at the end of the job log.
sed -n '/^There w\(as\|ere\) [0-9]* failure/,/^FAILURES!!!/p' BC-1/app/build/reports/androidTests/instrumentation.txt | head -120 || true
if rg 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' BC-1/app/build/reports/androidTests/instrumentation.txt; then exit 1; fi
rg '^OK \([0-9]+ tests?\)' BC-1/app/build/reports/androidTests/instrumentation.txt

# Validate the exported bytes independently of the Android media stack.
for name in acceptance-1080p-0 acceptance-1080p-1 acceptance-1080p-2 recovery-1080p; do
ACCEPTANCE_MP4="BC-1/app/build/reports/androidTests/qa/${name}.mp4"
ffmpeg -v error -xerror -i "$ACCEPTANCE_MP4" -f null -
ffprobe -v error -show_streams -show_format -of json "$ACCEPTANCE_MP4" > "${ACCEPTANCE_MP4}.ffprobe.json"
python3 - "${ACCEPTANCE_MP4}.ffprobe.json" <<'PYVERIFY'
import json, sys
from pathlib import Path
report=json.loads(Path(sys.argv[1]).read_text())
video=next(s for s in report['streams'] if s['codec_type']=='video')
audio=next(s for s in report['streams'] if s['codec_type']=='audio')
assert (video['codec_name'],video['width'],video['height'])==('h264',1920,1080), video
assert audio['codec_name']=='aac', audio
assert 3 < float(report['format']['duration']) < 5, report['format']
print('Independent MP4 check passed:', sys.argv[1], 'H.264 1920x1080 + AAC; decoded without errors')
PYVERIFY
done

# A/V speed changes must change both presentation duration and exported timestamps.
SPEED_MP4="BC-1/app/build/reports/androidTests/qa/speed-180p.mp4"
ffmpeg -v error -xerror -i "$SPEED_MP4" -f null -
ffprobe -v error -show_streams -show_format -of json "$SPEED_MP4" > "${SPEED_MP4}.ffprobe.json"
python3 - "${SPEED_MP4}.ffprobe.json" <<'PYVERIFY'
import json,sys
report=json.load(open(sys.argv[1]))
video=next(s for s in report['streams'] if s['codec_type']=='video')
audio=next(s for s in report['streams'] if s['codec_type']=='audio')
assert video['codec_name']=='h264' and audio['codec_name']=='aac'
assert 4.8<float(report['format']['duration'])<5.2, report['format']
assert abs(float(video['duration'])-float(audio['duration']))<.2, (video,audio)
print('Independent speed MP4 check passed: synchronized H.264/AAC, five seconds')
PYVERIFY
