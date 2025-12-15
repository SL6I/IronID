# Final IronID (Android)

Offline, privacy-first gym machine identifier built with CameraX and on-device TensorFlow Lite. Captures a photo (or gallery image), runs local inference, and instantly shows instructional videos for the detected machine—including multiple variations when available.

## Highlights
- On-device TFLite inference (no network needed).
- Camera capture + gallery import.
- Smart result sheet:
  - Plays instructional video for the detected machine.
  - Shows variation chips when multiple videos exist; switch instantly.
- Performance monitoring: `IronID_Performance` Logcat tag logs latency and confidence.
- Safety/UX:
  - Low confidence (<60%): hides video and shows toast “Confidence too low, please move closer.”
  - Permission denied: Snackbar + hint explaining camera access requirement.

## Videos & Resources
- Place videos in `app/src/main/res/raw/` using lowercase + underscores (e.g., `bench_press.mp4`).
- `VideoRepository` maps display labels (e.g., “Leg Press”, “Dumbbell”) to lists of `R.raw.*` entries; update as you add/rename files.

## Building & Running
```bash
./gradlew installDebug
```
- Ensure a device/emulator is connected and camera permission is granted at runtime.
- If resource merge fails, verify raw filenames follow Android rules: `[a-z0-9_]+`.

## Checking Performance Logs
```bash
adb logcat -s IronID_Performance
```
You’ll see entries like: `Inference Latency: 50 ms - Confidence: 97.25%`.

## Key UX/Error-Handling Behaviors
- Low confidence (<0.60f): no video playback; toast prompts user to move closer.
- Missing camera permission: Snackbar + hint; capture disabled until granted.

## Tech Stack
- Kotlin, AndroidX, CameraX, Material Components, TensorFlow Lite.

