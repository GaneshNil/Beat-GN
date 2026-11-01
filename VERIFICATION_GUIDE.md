# Verification guide

This guide covers installing the app and doing a first honest check. It does not promise that any
hardware feature works — that depends on the physical watch, and the test plan in
[LIMITATIONS_AND_DEVICE_TESTING.md](LIMITATIONS_AND_DEVICE_TESTING.md) records what to observe.

## 1. Prerequisites

* JDK 17 (Gradle 8.7 / AGP 8.4.1 will not run on JDK 21+).
* Android SDK with platform 35 and build-tools 35.0.0.
* An Android 11+ phone.
* A beatXP Marv Neo (or a device exposing service `FEEA` with characteristics `FEE1`, `FEE2`, `FEE3`).

## 2. Build and install

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk

./gradlew test lint assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 3. First run

1. Open **Marv Neo Companion**.
2. Allow **Nearby devices** (Android 12+) or **Location** (Android 11) and **Notifications**.
   The notification is the foreground service that keeps the link alive; it mirrors the real state.
3. Open **Watch** and tap **Scan**. Only devices actually heard are listed.
4. Tap the watch. The status must walk through Connecting → Discovering → Subscribing → Verifying.
5. **Ready** is only shown if the watch answered a battery read. Otherwise expect **Unstable** with a
   reason. That is correct behaviour, not a bug to work around.

If the OEM kills background links, use **Watch → Allow background connection** to open the system
battery-optimisation page, and if available allow autostart in the OEM settings. This app does not
silently request exotic permissions.

## 4. What to check first

| Check | Expected honest result |
| :--- | :--- |
| Status after connecting | `Ready` only with a real battery percentage; otherwise `Unstable` |
| Heart rate | A number only after a reading; a timeout message if the watch stays silent |
| SpO2 | Same rule |
| BP probe | A pair only if the watch answers; otherwise "never received" |
| Alarm save | Row with **pending** badge, never "synced" |
| Find watch | "Sent, not confirmed" |
| Assistant tab | Empty state explaining that the SDK has no transcript channel |
| Diagnostics | Real `TX`/`RX` hex lines |

## 5. Privacy check

* The app has no `INTERNET` permission.
* There is no API key field, no login, no analytics.
* Everything is stored in a local Room database (`marv_neo_health.db`).
* No notification listener service is declared in the manifest.

## 6. Common failure modes and their meaning

| Symptom | Meaning |
| :--- | :--- |
| "Crrepa data service (FEEA) is missing" | The device is not this hardware family, or it is a clone with a different profile |
| "Watch data channel incomplete (FEE1/FEE2/FEE3)" | The watch advertises `FEEA` but not all three channels; the vendor SDK would also reject it |
| "Unstable: watch did not answer a read request" | GATT connected, application layer silent. Often a watch that needs a phone-side bond or is already connected to another phone |
| Measurement timeout | The watch accepted the command but sent no reading |
| Reconnect attempts exhausted | Out of range, powered off, or bonded to another phone |

## 7. Getting help

Attach the **More → Diagnostics** log. It contains the exact frames, which is the only way to tell a
protocol problem from a hardware problem.
