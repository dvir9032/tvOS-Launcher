#!/usr/bin/env bash
# Joins the demo frames into a video and a GIF, holding each step and
# crossfading between them (the same way Shelf's backdrop changes).
set -euo pipefail

DIR=screenshots/demo
OUT=screenshots
HOLD=1.2   # seconds each frame is shown on its own
FADE=0.45  # seconds of crossfade between frames

frames=("$DIR"/*.png)
count=${#frames[@]}
if [ "$count" -lt 2 ]; then
  echo "not enough demo frames ($count)"
  exit 0
fi

inputs=()
for f in "${frames[@]}"; do
  inputs+=(-loop 1 -t "$(echo "$HOLD + $FADE" | bc)" -i "$f")
done

# [0][1]xfade -> [v1], [v1][2]xfade -> [v2] ... ; the k-th fade starts at k*HOLD
filter=""
prev="[0:v]"
for ((k = 1; k < count; k++)); do
  offset=$(echo "$k * $HOLD" | bc)
  filter+="${prev}[${k}:v]xfade=transition=fade:duration=${FADE}:offset=${offset}[v${k}];"
  prev="[v${k}]"
done
filter+="${prev}format=yuv420p,scale=1280:-2[out]"

ffmpeg -y -loglevel error "${inputs[@]}" -filter_complex "$filter" -map "[out]" \
  -r 30 -c:v libx264 -preset slow -crf 20 -movflags +faststart "$OUT/demo.mp4"

ffmpeg -y -loglevel error -i "$OUT/demo.mp4" \
  -vf "fps=12,scale=800:-1:flags=lanczos,split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4" \
  "$OUT/demo.gif"

ls -la "$OUT"/demo.*
