#!/usr/bin/env bash
set -euo pipefail
mkdir -p BC-1/app/src/androidTest/assets
for color in red blue; do
  frequency=440
  if [ "$color" = blue ]; then frequency=660; fi
  ffmpeg -hide_banner -loglevel error -y -f lavfi -i "color=c=$color:s=320x180:r=30:d=3" -f lavfi -i "sine=frequency=$frequency:sample_rate=48000:duration=3" -vf "drawbox=x=0:y=0:w=24:h=24:color=white:t=fill" -c:v libx264 -pix_fmt yuv420p -c:a aac -b:a 96k -shortest "BC-1/app/src/androidTest/assets/$color.mp4"
done
ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'sine=frequency=880:sample_rate=48000:duration=1' -c:a pcm_s16le BC-1/app/src/androidTest/assets/music.wav
