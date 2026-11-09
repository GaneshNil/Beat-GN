# Marv Neo Companion

A local-first Android companion for the **beatXP Marv Neo** (Crrepa/DaFit-class firmware), built with
Kotlin, Jetpack Compose and Room.

The guiding rule of this codebase: **never show a value, a state or a success that the watch did not
actually produce.** Where the protocol cannot confirm something, the UI says so.

---

## What works

| Area | Status |
| :--- | :--- |
| Scan / connect / reconnect over the verified Crrepa GATT profile | Implemented; needs a physical watch to verify end-to-end |
| "Connected" state | Only after service discovery, notification subscription and a real battery read |
| Time sync (`0x31`) | Implemented from verified bytecode |
| Heart rate (`2A37`) | Implemented; BPM comes from the standard characteristic |
| SpO2 (`0x6B`) | Implemented; only a real reply is stored |
| Blood pressure (`0x69`) | Probe only; shown only if the unit answers |
| Live steps (`FEE1`) | Implemented from the verified 3-byte triplet layout |
| Alarms (`0x11`) | Writes are sent; rows stay **pending** because firmware has no acknowledgement |
| Find watch (`0x61`) | Sent; reported as **unconfirmed** because the protocol has no reply |
| Reports (today / 7 days / month) | Local-midnight boundaries, deduplicated, one bucket per calendar day |
| Diagnostics | Raw TX/RX bytes for every exchange |

## What this app deliberately does not do

* No Gemini/AI answers, API keys, accounts or network calls (there is no `INTERNET` permission).
* No assistant transcript invention — the vendor SDK exposes no watch→phone transcript channel.
* No notification scraping, microphone triggers, accessibility tricks or background app reading.
* No watch-face "flashing", app installation or firmware update.
* No claims of alarm or find-watch confirmation.
* No medical interpretation: values come from an optical wrist sensor and can be wrong during motion.

## Design

Graphite/ink neutrals, warm sand in light mode, one copper accent, semantic colours only for data
(heart, oxygen, motion) and status (positive/warning/critical). No dynamic colour, no decorative
gradients, no neon. Screens are `LazyColumn`-based with weight-driven metric rows so small phones do
not clip.

Navigation: Home · Health · Watch · Assistant · More.

## Build

Requires JDK 17 and Android SDK 35.

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
./gradlew test lint assembleDebug
```

Last verified run on 2026-11-09: **75 unit tests pass (0 failures, 0 errors, 0 skipped), lint
reports 0 errors and 35 warnings, debug APK builds.** Details and the APK SHA-256 are in
[TEST_MATRIX.md](TEST_MATRIX.md). Bluetooth, alarm and rendering behaviour still require the
physical-device plan in [LIMITATIONS_AND_DEVICE_TESTING.md](LIMITATIONS_AND_DEVICE_TESTING.md).

Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Documentation

* [PROTOCOL_EVIDENCE.md](PROTOCOL_EVIDENCE.md) — every BLE claim with its bytecode citation and a
  CONFIRMED / INFERRED / UNKNOWN label.
* [ROOT_CAUSE_MATRIX.md](ROOT_CAUSE_MATRIX.md) — each previous defect, why it happened, what changed.
* [APK_COMPARISON.md](APK_COMPARISON.md) — reference vendor APK vs previous app vs this rewrite.
* [TEST_MATRIX.md](TEST_MATRIX.md) — automated test status and blocked hardware tests.
* [LIMITATIONS_AND_DEVICE_TESTING.md](LIMITATIONS_AND_DEVICE_TESTING.md) — limitations and a
  physical-device test plan.
* [VERIFICATION_GUIDE.md](VERIFICATION_GUIDE.md) — install and first-run steps.
