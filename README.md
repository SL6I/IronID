# IronID — Android Deployment 📱

> **Offline, privacy-first gym machine identifier powered by on-device AI**

![Build Status](https://img.shields.io/badge/build-passing-brightgreen)

This repository contains the source code for the **IronID Android application**, which deploys an optimized **TensorFlow Lite (TFLite)** model to provide **real-time, fully offline gym equipment recognition** with integrated instructional guides.

---

## 🚀 App Overview

**IronID** is a native Android application built with **Kotlin** and **CameraX**.  
It acts as the user-facing layer of the IronID ecosystem, translating advanced machine learning models into a simple, practical tool for everyday gym users.

### ✨ Key Features

- **📸 Instant Identification**
  - Capture a photo using the camera or import one from the gallery.
  - Automatically identifies gym machines in real time.

- **⚡ Offline Inference**
  - Runs entirely on-device using TensorFlow Lite.
  - No internet connection required.

- **🎥 Smart Instruction Hub**
  - Automatically plays exercise tutorial videos for detected equipment.
  - **Variation Chips** allow users to switch between multiple exercises for the same machine  
    (e.g., *Incline Bench* vs. *Flat Bench*).

- **🛡️ Privacy & Safety**
  - **Confidence Guardrails:** Predictions with confidence **< 60%** are hidden.
  - **User Feedback:** Clear prompts such as  
    *“Confidence too low, please move closer”* guide better image capture.
  - No images or data leave the device.

---

## 🛠️ Tech Stack

| Component | Technology |
|---------|------------|
| **Language** | Kotlin |
| **UI Toolkit** | XML / Material Components |
| **Camera** | Android CameraX API |
| **ML Runtime** | TensorFlow Lite (TFLite) |
| **Build System** | Gradle (Kotlin DSL) |
| **CI/CD** | GitHub Actions |

---

## 📂 Repository Structure

```text
IronID-Android/
├── .github/workflows/       # CI/CD pipelines for automated build checks
├── app/                     # Main Android module (source, resources, manifest)
├── gradle/                  # Gradle wrapper files
├── build.gradle.kts         # Root project build configuration
├── settings.gradle.kts      # Project settings and module inclusion
├── setup_dummy_assets.sh    # Script to generate dummy assets for CI environments
├── ironid.log               # Local build/runtime logs
└── README.md                # Project documentation
```

## ⚙️ Setup & Installation

### 1️⃣ Prerequisites

Before building and running the project, ensure the following tools are installed:

- **Android Studio** (latest stable version)
- **JDK 17** or higher
- **Android device or emulator** with camera support enabled

---

### 2️⃣ Clone & Build

Clone the repository and install the debug build on a connected device or emulator:

```bash
git clone https://github.com/YourUsername/IronID-Android.git
cd IronID-Android
./gradlew installDebug
```


## 3️⃣ CI/CD & Testing

The project includes an automated **GitHub Actions** pipeline (**Android CI**) that runs on every push.

- **Status:** Passing (1 successful check)
- **Purpose:** Ensures the application builds correctly in clean and isolated environments

### Asset Setup for CI

In CI environments where real media assets are unavailable, generate dummy assets using:

```bash
chmod +x setup_dummy_assets.sh
./setup_dummy_assets.sh
```

## 📹 Managing Video Resources

IronID maps detected machine labels directly to **local instructional videos** stored within the application.

### ➕ Adding or Updating Videos

#### 1️⃣ Add Video Files

Place `.mp4` files in the following directory:

```text
app/src/main/res/raw/
```


### 2️⃣ Naming Convention

- Use **lowercase letters only**
- Use **underscores (`_`) instead of spaces**

**Examples:**
- `bench_press.mp4`
- `cable_row.mp4`

> ⚠️ Android resource names cannot contain capital letters or spaces.

---

### 3️⃣ Update the Mapping

Open `VideoRepository.kt` and map the model’s output labels to the corresponding resource IDs:

```kotlin
// Example in VideoRepository.kt
"Leg Press" -> listOf(
    R.raw.leg_press_standard,
    R.raw.leg_press_wide_stance
)
```

## 📊 Performance & Profiling

The application is optimized for **smooth, real-time inference** on mobile devices.

### 1️⃣ Real-World Inference Logs

- **Average inference time:** 30–45 ms  
- **Test devices:** Standard mid-range Android devices  
<img width="1505" height="562" alt="Screenshot 2025-12-14 145000" src="https://github.com/user-attachments/assets/85be0d12-3a11-4fec-a9d6-c4f3d8e2f2a4" />

To view live performance logs:

```bash
adb logcat -s IronID_Performance
```

**Sample Output:**

```text
D/IronID_Performance: Inference Latency: 44 ms - Confidence: 88.56%
D/IronID_Performance: Inference Latency: 35 ms - Confidence: 99.66%
D/IronID_Performance: Inference Latency: 30 ms - Confidence: 94.90%
D/IronID_Performance: Inference Latency: 31 ms - Confidence: 94.90%
D/IronID_Performance: Inference Latency: 36 ms - Confidence: 60.72%
```

### 2️⃣ Resource Efficiency (Android Profiler)

Testing using **Android Studio Profiler** confirms stable and lightweight performance:

- **CPU:** Negligible usage (≈ 0% when idle or viewing results)
- **Memory:** ~230 MB total
  - Java Heap: ~14 MB
  - Native: ~118 MB
- **Leak Detection:** No memory spikes observed during repeated inference cycles

---

## 🧩 UX & Error Handling Patterns

### 🔻 Low Confidence (< 0.60)

- **Action:** Video playback is blocked
- **UI Feedback:**  
  Toast message displayed:
  > *“Confidence too low, please move closer”*
- **Reason:** Prevents showing incorrect or misleading exercise guidance

---

### 📷 Permissions Handling

If **Camera Permission** is denied:

- Capture functionality is disabled
- A **Snackbar** appears with an action button directing the user to system settings
- Normal functionality resumes once permission is granted

---

## ✅ Summary

IronID delivers a **fully offline**, **privacy-first**, and **high-performance** gym machine identification experience on Android.  
Its clean architecture, strict confidence thresholds, and efficient on-device inference make it suitable for real-world fitness environments without compromising user privacy.
