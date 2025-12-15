#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAW_DIR="$ROOT_DIR/app/src/main/res/raw"

mkdir -p "$RAW_DIR"

videos=(
  "barbell_deadlifts.mp4"
  "squats.mp4"
  "bench_press.mp4"
  "dumbbell.mp4"
  "kettlebell.mp4"
  "leg_press.mp4"
  "punching_bag.mp4"
  "ab_roller.mp4"
  "stationary_bicycle.mp4"
  "step_platform.mp4"
  "treadmill.mp4"
)

for video in "${videos[@]}"; do
  touch "$RAW_DIR/$video"
done

echo "Dummy video assets created in $RAW_DIR"










