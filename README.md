# SmartFit BLE

An Android app that connects to Bluetooth Low Energy (BLE) blood pressure monitors, collects health vitals in real time, and gives users a dashboard, history, and trend charts for their data — with local storage, cloud backup, and AI-powered health insights.

## Features

- **BLE device scanning & connection** — pairs with BLE blood pressure/health monitors and streams live readings
- **Vitals dashboard** — latest heart rate, blood pressure, SpO2, and temperature at a glance
- **History** — date-grouped historical records with manual entry and editing
- **Trend charts** — interactive graphs of vitals over time (MPAndroidChart)
- **AI health insights** — chat-based summaries and risk flags powered by Groq
- **Cloud sync** — Firebase Firestore backup with Firebase Auth / Google Sign-In
- **Offline-first** — local persistence via Room, so the app works without connectivity
- **Dark mode & localization** — includes Hindi (`values-hi`) string resources

## Architecture

The app follows **MVVM** with a repository layer as the single source of truth:

```
View (Activities/Fragments)
      │  observes StateFlow
      ▼
ViewModel (business logic, UI state)
      │  calls
      ▼
Repository (Room + Firebase + BLE)
      │
      ▼
Data Layer (HealthDatabase, FirebaseHealthDataSource,
            FirebaseUserDataSource, BleRepository, SessionManager)
```

Dependencies are wired manually through an `AppContainer` (no DI framework). See [SmartFit_BLE_Architecture_Documentation.md](SmartFit_BLE_Architecture_Documentation.md) for a full file-by-file breakdown.

## Tech stack

| Layer | Tech |
|---|---|
| Language | Kotlin |
| UI | View Binding, Navigation Component, Material Components |
| Async | Coroutines, StateFlow |
| Local storage | Room, DataStore |
| Cloud | Firebase Auth, Firestore, Google Sign-In |
| Charts | MPAndroidChart |
| AI | Groq API (chat completions) |
| Build | Gradle Kotlin DSL, KSP |

- `minSdk` 23, `targetSdk` / `compileSdk` 34
- JVM target 17

## Project structure

```
app/src/main/java/com/smartfit/ble/
├── di/            # AppContainer (manual dependency injection)
├── data/          # models, Room database, Firebase sources, BLE repo, AI service
└── ui/            # splash, login, main, dashboard, history, graphs, devices,
                   #   add-entry, profile, information (AI chat)
shared/            # shared module (BLE protocol / cross-cutting code)
firmware/          # ESP32 firmware for the BLE health monitor device
```

## Firmware

[`firmware/sketch_apr30a_latest/`](firmware/sketch_apr30a_latest) contains the Arduino sketch that runs on the ESP32-based BLE device. It:

- Reads heart rate and SpO2 from a MAX30105 pulse oximeter
- Reads body temperature from an MLX90614 IR sensor
- Shows live readings on an SSD1306 OLED display
- Advertises a BLE GATT service/characteristic that the Android app connects to and reads vitals from

Flash it with the Arduino IDE (install the MAX30105, Adafruit SSD1306/GFX, Adafruit MLX90614, and ESP32 BLE libraries first).

## Getting started

### Prerequisites

- Android Studio (latest stable)
- JDK 17
- A Firebase project with an Android app registered under the applicationId `com.smartfit.ble`
- A Groq API key (for AI insights)

### Setup

1. Clone the repo:
   ```bash
   git clone https://github.com/BhavyaPatel19/SmartFitBLE.git
   ```
2. Add your Firebase config file at `app/google-services.json` (not committed — get it from the Firebase console).
3. Add your Groq API key so it's exposed via `BuildConfig.GROQ_API_KEY`, e.g. in `local.properties`:
   ```properties
   GROQ_API_KEY=your_key_here
   ```
   and read it into `app/build.gradle.kts`:
   ```kotlin
   buildConfigField("String", "GROQ_API_KEY", "\"${'$'}{project.findProperty("GROQ_API_KEY") ?: ""}\"")
   ```
4. Open the project in Android Studio and let Gradle sync, or build from the command line:
   ```bash
   ./gradlew assembleDebug
   ```

## Branches

- `AI-features` — active development branch (default), includes the Groq-powered AI insights and trend graphs work.

## License

No license file is currently included — all rights reserved by the author unless stated otherwise.
