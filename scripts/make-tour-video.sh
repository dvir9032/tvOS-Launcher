#!/usr/bin/env bash
# Turns the tour frames (record-tour.sh) into screenshots/tour.mp4: each frame
# gets its caption in a glass pill at the bottom, then frames crossfade.
set -euo pipefail

DIR=screenshots/tour
OUT=screenshots
FONT=app/src/main/res/font/assistant_semibold.ttf
HOLD=2.0
FADE=0.5

[ -f "$DIR/captions.txt" ] || { echo "no tour frames"; exit 0; }
mkdir -p "$DIR/captioned"

frames=()
while IFS='|' read -r num text; do
  [ -f "$DIR/$num.png" ] || continue
  printf '%s' "$text" > "$DIR/captioned/$num.txt"
  ffmpeg -nostdin -y -loglevel error -i "$DIR/$num.png" -vf \
    "drawtext=fontfile=$FONT:textfile=$DIR/captioned/$num.txt:fontsize=36:fontcolor=white:x=(w-text_w)/2:y=h-68:box=1:boxcolor=black@0.55:boxborderw=16" \
    "$DIR/captioned/$num.png"
  frames+=("$DIR/captioned/$num.png")
done < "$DIR/captions.txt"

count=${#frames[@]}
[ "$count" -ge 2 ] || { echo "not enough tour frames ($count)"; exit 0; }

inputs=()
for f in "${frames[@]}"; do
  inputs+=(-loop 1 -t "$(echo "$HOLD + $FADE" | bc)" -i "$f")
done
filter=""
prev="[0:v]"
for ((k = 1; k < count; k++)); do
  offset=$(echo "$k * $HOLD" | bc)
  filter+="${prev}[${k}:v]xfade=transition=fade:duration=${FADE}:offset=${offset}[v${k}];"
  prev="[v${k}]"
done
filter+="${prev}format=yuv420p,scale=1920:-2[out]"

ffmpeg -y -loglevel error "${inputs[@]}" -filter_complex "$filter" -map "[out]" \
  -r 30 -c:v libx264 -preset medium -crf 21 -movflags +faststart "$OUT/tour.mp4"
ls -la "$OUT/tour.mp4"
