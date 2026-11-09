# Project status — audit and repair pass

Last updated: 2026-11-09

## Repository state

* Repository: `GaneshNil/Beat-GN`
* Checkout: `/home/ganesh/Documents/antigravity/Beat-GN`
* Base commit (before this pass): `2c7881f180ad46501b358b48932551e1bd3ed2de` (`main`)
* Working branch: `audit/fix-p0-p1`
* Repair commit: `310269a` — `fix: repair audit defects in BLE, protocol, storage and UI`
* This status/documentation change set is committed separately after `310269a`.

## Environment

* JDK 17.0.21 at `/home/ganesh/android-toolchain/jdk`
* Android SDK platform 35, build-tools 35.0.0 and 34.0.0, platform-tools
* Gradle 8.7 (wrapper distribution cached under `~/.gradle/wrapper/dists`)
* AGP 8.4.1 with the known compileSdk 35 warning (not an error)
* No physical watch and no emulator are attached

## Completed

1. Read every project document, Gradle/manifest file, all Kotlin sources and both pre-existing test files.
2. Produced `AUDIT_FINDINGS.md` with 37 findings (F-01…F-37), each classified by evidence.
3. Applied source fixes:
   * Room v1→v2 now creates `voice_messages`; migrations centralised in `MigrationSql.kt`;
     `exportSchema = true`, committed schema `app/schemas/.../4.json`.
   * BLE manager: API 30–32 read callback, stale-GATT guards, bounded reconnect, explicit
     `reconnect()`, stop-scan state, telemetry clearing, address redaction, concurrent notify set,
     per-measurement timeouts with stop commands, nullable step fields, `Unstable` recovery.
   * Protocol: 3/6/9/12-byte step payloads, all-`0xFF` → `null`, BP bounds `60..255`/`30..160`
     with systolic > diastolic, frame version-byte resync.
   * Health data: BP duplicate filtering, persisted "BP ever recorded", injected clock/zone.
   * UI: failed alarm write → `FAILED`, honest "no days" copy, phone-only deletion notice,
     first-free slot allocation, scrollable day chips, chart label thinning, null-day bars,
     fully clickable More rows.
   * Privacy: `allowBackup="false"`; failure logs contain no health data; diagnostics redact MAC.
4. Added automated tests (75 total):
   * `CrrepaProtocolTest` — 32
   * `HealthAggregatorTest` — 23 (DST, time-zone change, year rollover, empty windows, dedupe)
   * `MigrationSqlTest` — 6 (real SQLite v1→v4 using `sqlite-jdbc`)
   * `HealthRepositoryTest` — 6 (fake DAO; dedupe, validity, persisted BP flag)
   * `AlarmSlotsTest` — 4
   * `SparkLabelIndicesTest` — 4

## Verification results (2026-11-09)

Command (repository root):

```bash
JAVA_HOME=/home/ganesh/android-toolchain/jdk \
ANDROID_HOME=/home/ganesh/android-toolchain/sdk \
ANDROID_SDK_ROOT=/home/ganesh/android-toolchain/sdk \
./gradlew clean testDebugUnitTest lintDebug assembleDebug --console=plain
```

| Task | Result |
| :--- | :--- |
| `testDebugUnitTest` | **PASS** — 75 tests, 0 failures, 0 errors, 0 skipped |
| `lintDebug` | **PASS** — 0 errors, 35 warnings |
| `assembleDebug` | **PASS** — `BUILD SUCCESSFUL in 32s` |
| Room schema export | **PASS** — version 4, 7 entities, identity `ccbd5b6a7f96734499503ec951d86e55` |

Artifact:

* `app/build/outputs/apk/debug/app-debug.apk`
* Size: 53,351,874 bytes
* SHA-256: `b58b55143c77dd83793db2ee0414226b9ad8964924d18bf43757113ae19b1c8d`
* Debug-signed; not a release artifact.

Kotlin compiler warnings only (unused parameters/variable); no error-severity lint issue. The
clean build rebootstrapped build-tools 34 because AGP 8.4.1 requests it; no source change resulted.

## Still blocked — nothing below is claimed as passing

* Physical watch: scan/connect/Ready, battery read, time sync, HR, SpO2, BP, alarm firing,
  find-watch vibration, reconnect, screen-off background.
* Alarm acknowledgement and watch-side alarm deletion (firmware has no acknowledged write/delete).
* On-device Room migration through the Android framework (the exact SQL is covered on the JVM).
* UI rendering on small/standard/large phones and dark/light modes (no emulator or device).

The manual procedure is in `LIMITATIONS_AND_DEVICE_TESTING.md`.

## Deviations / decisions

* Report windows deliberately remain `startOfDay..now` (F-24) so `aggregate(nowMillis = window.endMillis)`
  cannot roll into the next day. DST, zone-change and year-rollover tests cover the boundary logic.
* Alarm deletion is phone-only; the UI states this. No unverified delete opcode was invented.
* The reported BP systolic maximum is 255 (single unsigned byte); the earlier 260 bound was
  unreachable and was corrected together with its test.
