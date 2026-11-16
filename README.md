# Marv Neo Companion

Beat-GN is a local-first Android companion for the beatXP Marv Neo. It uses Kotlin, Jetpack Compose,
Room and Android Bluetooth GATT. The target watch runs firmware V006213.

The app scans for Bluetooth Low Energy devices and verifies a selected link using the documented
Crrepa/DaFit-family GATT profile and a valid battery read. It handles live activity data, pulse and
oxygen measurements, an optional blood-pressure probe, local reports, repeating alarms, time sync,
and a find-watch command. Alarm and find-watch status reflects the Bluetooth write. Check the watch
for the result of these commands.

Local reports use data received while Beat-GN is connected. This build has no watch history import
or active transcript delivery. Diagnostics show connection status, characteristic IDs, opcodes and
packet sizes. The app requests no internet access. Room data and watch preferences are excluded
from Android backup and device transfer.

## Build and test

Requires JDK 17 and Android SDK 35. The wrapper uses Gradle 8.7 with Android Gradle Plugin 8.6.1.

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Run `./gradlew connectedDebugAndroidTest` when a phone or emulator is visible to ADB. The test uses
Android SQLite and Room to check a v1 upgrade and alarm persistence across reopening.

## Verification

- [V006213 compatibility](MARV_NEO_V006213_COMPATIBILITY.md) lists the evidence, GATT profile, supported protocol operations and watch-dependent unknowns.
- [Verification matrix](FEATURE_VERIFICATION_MATRIX.md) and [test results](TEST_RESULTS.md) distinguish local tests from blocked device scenarios.
- [Physical device checklist](DEVICE_TESTING_CHECKLIST.md) covers setup, model and firmware identity, expected watch behavior and diagnostic collection.
- [Final audit](FINAL_AUDIT_REPORT.md), [change log](BUG_FIX_CHANGELOG.md) and [recommendations](FEATURE_RECOMMENDATIONS.md) record the repairs and next evidence to collect.
- [Protocol evidence](PROTOCOL_EVIDENCE.md) and [APK comparison](APK_COMPARISON.md) record historical family-level findings.
