# 🎾 Padel Score for Wear OS

<!--suppress ALL -->

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="128" height="128" alt="Padel Score App Icon" />
</p>

<p align="center">
  <strong>A standalone, privacy-first Wear OS smartwatch app for keeping score during padel matches — 100% offline, fast, and effortless.</strong>
</p>

<p align="center">
  <a href="https://github.com/Hygros/PadelScore/releases"><img src="https://img.shields.io/github/v/release/Hygros/PadelScore?color=2e7d32&label=Latest%20Release&style=flat-square" alt="Latest Release" /></a>
  <a href="https://developer.android.com/wear"><img src="https://img.shields.io/badge/Platform-Wear%20OS%203%2B-00C853?style=flat-square&logo=wearos&logoColor=white" alt="Wear OS 3+" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square" alt="License" /></a>
  <a href="#privacy"><img src="https://img.shields.io/badge/Privacy-100%25%20Offline-brightgreen?style=flat-square" alt="Offline Scoring" /></a>
</p>

---

## 📱 Screenshots

<p align="center">
  <img src="docs/screenshots/01_new_match.png" width="200" alt="New Match Setup" />
  &nbsp;&nbsp;
  <img src="docs/screenshots/02_match_scoring.png" width="200" alt="Match Scoring" />
  &nbsp;&nbsp;
  <img src="docs/screenshots/03_match_result.png" width="200" alt="Match Result" />
  &nbsp;&nbsp;
  <img src="docs/screenshots/04_match_history.png" width="200" alt="Match History" />
</p>

<p align="center">
  <sub><b>Match Setup</b> &nbsp;•&nbsp; <b>Live Scoring</b> &nbsp;•&nbsp; <b>Match Summary</b> &nbsp;•&nbsp; <b>Local History</b></sub>
</p>

---

## Support the project

If this project has been helpful to you, you can support its continued development:

[☕ Buy me a coffee](https://buymeacoffee.com/YOUR_USERNAME)

Contributions, bug reports, and feature suggestions are equally appreciated.

---

## ✨ Features

- 🎾 **Official Padel Rules**: Advantage, Golden Point, and Star Point scoring modes.
- ⚙️ **Flexible Match Formats**: Single set, Best-of-3, Short sets (to 4 games), Normal sets (to 6 games), set tie-breaks, and match tie-breaks.
- 🟡 **Serve & Court Tracking**: Visual serve indicators (Deuce/Ad court) with 1-2-2 tie-break rotation and court change notifications.
- ❤️ **Fitness Tracking**: Native integration with Wear OS Health Services to track heart rate, steps, active calories, and match duration as an official workout.
- 📱 **Health Connect Sync**: Optional phone companion app to automatically sync your padel workouts and heart rate data from the watch directly to Android Health Connect.
- ⌚ **Tiles & Complications**: Launch a new match instantly straight from your watch face using the included Complication or Wear OS Tile.
- 🔕 **Match Quiet Mode**: Optional automatic Do Not Disturb mode during active matches to silence notifications and pop-ups.
- 🎯 **Court-Optimized UI**: Large touch targets, 300 ms double-tap safety lock, wrist haptics, and Always-On Display (AOD) support.
- ↺ **Instant Undo & Reset**: Quick point reversal and safety long-press match reset.
- 📜 **Local Match History**: Save and review past matches on your watch, including scores, durations, and total point breakdowns.
- 🔒 **100% Standalone & Privacy-First**: No internet permission requested and zero tracking. The watch app operates fully independently.

---

## 🚀 Installation

1. Download the latest release APKs from [GitHub Releases](https://github.com/Hygros/PadelScore/releases).
2. Install the Wear OS app onto your watch using **ADB over Wi-Fi** or a phone sideloading app (e.g. Bugjaeger, Easy Fire Tools):
   ```bash
   adb connect <WATCH_IP_ADDRESS>:5555
   adb install -r PadelScore-wear.apk
   ```
*(Note: To enable Health Connect sync, install the companion `PadelScore-mobile.apk` on your paired Android phone. The phone app acts purely as a sync bridge.)*

---

<a id="privacy"></a>
## 🔒 Privacy & Permissions

- ❌ **No Internet Access**: `android.permission.INTERNET` is not requested by either the watch or phone app.
- ❌ **No Tracking**: No telemetry, analytics, or background cloud sync.
- 💾 **100% Local**: All match state and history remain strictly on your watch. If using the companion app, health data is only passed directly from the watch to your phone's secure on-device Health Connect store.

---

## 📂 Project Structure

```text
app/src/main/java/ch/hygro/padelscore/
├── billing/        # Optional Supporter page Google Play Billing
├── complications/  # Watch face Complication for quick-start
├── health/         # Health Services fitness tracking (Heart Rate, Steps)
├── logic/          # Core scoring engine & serve calculator
├── model/          # Match configuration and scoring data models
├── presentation/   # Wear OS Jetpack Compose UI & quiet mode controller
├── storage/        # Offline local match storage & persistence
├── tiles/          # Wear OS quick-start Tile
└── vibration/      # Wrist haptic feedback engine

mobile/src/main/java/ch/hygro/padelscore/sync/
└──                 # Optional companion app for Health Connect workout sync
```

---

## 📄 License

This project is licensed under the [Apache License 2.0](LICENSE).
