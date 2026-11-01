# Test matrix

Environment for the automated runs:

* JDK 17 (`/home/ganesh/android-toolchain/jdk`)
* Android SDK platform 35, build-tools 35.0.0
* Gradle 8.7 / AGP 8.4.1

Commands:

```bash
./gradlew test lint assembleDebug
```

## 1. Automated unit tests

Pure JVM tests; no Android device required. Run with `./gradlew testDebugUnitTest`.

### `CrrepaProtocolTest` — wire format

| Test | What it proves |
| :--- | :--- |
| frame header/version/length | `FE EA`, `0x10`/`0x20`, total length includes header |
| parse round-trip | opcode and payload survive framing |
| parse rejects bad magic/short/impossible length/trailing bytes | malformed input never becomes data |
| split across three notifications | chunked reassembly produces exactly one frame |
| two frames in one notification | stream is drained, not truncated |
| leading garbage | resynchronisation drops bytes and still finds the frame |
| impossible declared length | garbage is not interpreted as a packet |
| time sync payload | LE32 unix seconds + whole-hour offset |
| HR start/stop | `0x6D` with `{0}` / `{0xFF}` |
| BP start/stop | `0x69` with `{0,0,0}` / `{-1,-1,-1}` |
| `2A37` 8-bit and 16-bit BPM | correct flags handling |
| SpO2 byte | 1–100 accepted, 0/101/empty rejected |
| BP 3-byte reply | systolic = byte 1, diastolic = byte 2 |
| step triplets | 24-bit LE steps/distance/calories/active minutes |
| alarm 8-byte layout | slot, enable, mode, hour, minute, date, mask |
| one-off date code | `((year-2015)<<12)|(month<<8)|day` |
| repeat mask bits | Sunday = bit 0 … Saturday = bit 6 |
| query alarms | `0xB9` with `{0x15,0x04}` |

### `HealthAggregatorTest` — reports

| Test | What it proves |
| :--- | :--- |
| today boundary | local midnight, not `now - 24h` |
| 7-day boundary | today + 6 preceding calendar days |
| month boundary | first of month at 00:00, including rollover |
| HR validity | only 25–250 |
| SpO2 validity | only 50–100 |
| BP validity | range plus systolic > diastolic |
| duplicate within 3 s | counted once |
| duplicate after 3 s | counted separately |
| different values close together | both kept |
| stats | count/average/min/max |
| empty stats | average is null, not 0 |
| window filtering | yesterday's reading excluded from today |
| daily buckets | one per day, gaps are null |
| daily average | computed from that day only |

## 2. Gradle tasks — results of the verification run (2026-11-01)

Command:

```bash
JAVA_HOME=<jdk17> ANDROID_HOME=<sdk> ./gradlew testDebugUnitTest lintDebug assembleDebug
```

| Task | Result | Notes |
| :--- | :--- | :--- |
| `testDebugUnitTest` | **PASS** — 37 tests, 0 failures, 0 errors, 0 skipped (`CrrepaProtocolTest` 22, `HealthAggregatorTest` 15) | pure JVM; no device needed |
| `lintDebug` | **PASS** after fixing 2 errors; final run: 0 errors, 34 warnings |
| `assembleDebug` | **PASS**, `BUILD SUCCESSFUL in 4m 45s` | `app/build/outputs/apk/debug/app-debug.apk` (copied to `Beat-GN-debug-apk/`, SHA-256 `308cfad7ee36865c284d26de694864f2c33b2f8354952e6e7ca1bb0cdc0955a7`) |

Issues found and fixed during this run (both real, both fixed in source):

1. `WrongConstant` (error): `writeCharacteristic` on API 33+ returns a `BluetoothStatusCodes`
   value; the code compared against the numerically identical `BluetoothGatt.GATT_SUCCESS`.
   Changed to `BluetoothStatusCodes.SUCCESS` (same value 0, correct constant).
2. `CoarseFineLocation` (error): `ACCESS_FINE_LOCATION` was declared for the Android 11 scan path
   without the paired `ACCESS_COARSE_LOCATION`. Added with `maxSdkVersion="30"`.

Earlier runs in this session also verified:

* `:app:compileDebugKotlin` — PASS after fixing an unresolved `MeasurementState.Completed.secondary`
  reference in `HealthScreen.kt`.
* One unit-test compile error (JUnit `assertEquals` argument order) fixed in `CrrepaProtocolTest`.
* Incremental re-run after the Assistant/Watch copy changes and the two lint fixes — see run log.

## 3. Blocked tests (hardware)

| Test | State | Reason |
| :--- | :--- | :--- |
| Scan → connect → Ready | BLOCKED | no physical watch |
| Time sync visible on watch | BLOCKED | no physical watch |
| Live HR / SpO2 / BP | BLOCKED | no physical watch |
| Alarm stored and fired | UNOBSERVABLE | firmware has no write acknowledgement |
| Find watch vibrates | UNOBSERVABLE | no reply opcode |
| Battery read value | BLOCKED | no physical watch |
| Reconnect after range loss | BLOCKED | no physical watch |
| UI on small/standard/large phone | BLOCKED | no emulator/system image or device attached |

See [LIMITATIONS_AND_DEVICE_TESTING.md](LIMITATIONS_AND_DEVICE_TESTING.md) for the manual procedure.

## 4. Static checks that replace some runtime checks

| Check | Method |
| :--- | :--- |
| No fabricated assistant | `grep` for `GEMINI`, `generativelanguage`, `Gemini` outside the explanatory text |
| No network | manifest has no `INTERNET` permission |
| No notification scraping | no `NotificationListenerService` in manifest or source |
| No fake success strings | alarm/find outcomes only `AwaitingAcknowledgement` or `Failed` |
| Database migration additive | `MIGRATION_3_4` only `ALTER TABLE ... ADD COLUMN` |
