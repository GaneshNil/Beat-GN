# Test matrix

Environment for the automated runs:

* JDK 17 (`/home/ganesh/android-toolchain/jdk`)
* Android SDK platform 35, build-tools 35.0.0
* Gradle 8.7 / AGP 8.4.1

Commands:

```bash
JAVA_HOME=<jdk17> ANDROID_HOME=<sdk> ./gradlew clean testDebugUnitTest lintDebug assembleDebug
```

## 1. Automated unit tests

Pure JVM tests; no Android device required. The 2026-11-09 run executed **75 tests: 0 failures,
0 errors, 0 skipped**.

### `CrrepaProtocolTest` — wire format and parser bounds (32 tests)

| Test | What it proves |
| :--- | :--- |
| frame header/version/length | `FE EA`, accepted version bytes `0x10`/`0x20`, total length includes header |
| parse round-trip | opcode and payload survive framing |
| parse rejects bad magic/short/impossible length/trailing bytes | malformed input never becomes data |
| split across three notifications | chunked reassembly produces exactly one frame |
| two frames in one notification | stream is drained, not truncated |
| leading garbage / bad version byte | resynchronisation drops only unusable bytes and still finds the next frame |
| impossible declared length | garbage is not interpreted as a packet |
| time sync payload | LE32 unix seconds + whole-hour offset |
| HR start/stop | `0x6D` with `{0}` / `{0xFF}` |
| BP start/stop | `0x69` with `{0,0,0}` / `{-1,-1,-1}` |
| `2A37` 8-bit and 16-bit BPM | correct flags handling |
| SpO2 byte | 1–100 accepted, 0/101/empty rejected |
| BP 3-byte reply | systolic = byte 1, diastolic = byte 2 |
| BP bounds | 60–255 systolic, 30–160 diastolic, systolic > diastolic (255 is the byte maximum) |
| BP payload length | exactly three bytes |
| step triplets | 3/6/9/12-byte payloads, 24-bit LE steps/distance/calories/active minutes |
| all-`0xFF` step field | mapped to `null`, never `16777215` |
| invalid step payload sizes | rejected |
| alarm 8-byte layout | slot, enable, mode, hour, minute, date, mask |
| one-off date code | `((year-2015)<<12)|(month<<8)|day` |
| repeat mask bits | Sunday = bit 0 … Saturday = bit 6 |
| query alarms | `0xB9` with `{0x15,0x04}` |

### `HealthAggregatorTest` — reports (23 tests)

| Test | What it proves |
| :--- | :--- |
| today boundary | local midnight, not `now - 24h` |
| 7-day boundary | today + 6 preceding calendar days |
| month boundary | first of month at 00:00, including rollover |
| DST spring-forward / fall-back | day boundaries stay at local midnight |
| time-zone change | windows follow the injected zone |
| year rollover | December/January windows stay contiguous |
| empty 7-day set | seven `null` buckets, no invented zeroes |
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

### `MigrationSqlTest` — real SQLite migration fixture (6 tests)

Applies the exact `MigrationSql` lists that `AppDatabase` ships to an in-memory SQLite database
via `sqlite-jdbc` (JVM substitute for `MigrationTestHelper`).

| Test | What it proves |
| :--- | :--- |
| v1 → v4 creates every missing table | `voice_messages`, `blood_pressure_record`, `alarm_record` exist |
| v1 → v2 creates the transcript table | old empty migration is actually repaired; column order/types match |
| v1 → v2 is idempotent | re-running does not fail |
| v3 → v4 alarm columns | `syncState` defaults to `PENDING`, `updatedAt` defaults to `0`, both `NOT NULL` |
| v3 → v4 transcript column | `sessionId` is nullable `TEXT` |
| inserted alarm without state | reads back as `PENDING` / `0` |

Existing v1 health rows are asserted to survive the full chain.

### `HealthRepositoryTest` — dedupe and persisted BP flag (6 tests)

Uses a fake `HealthDao`; no database or device.

| Test | What it proves |
| :--- | :--- |
| identical BP within 5 s | `DUPLICATE`, one row |
| identical BP after 5 s | `STORED`, two rows |
| different BP within the window | `STORED` |
| implausible BP | `INVALID`, nothing inserted |
| BP ever recorded | `false` before the first write, `true` after (survives process restart because it is DAO-backed) |
| heart-rate dedupe/invalidity | same 5 s window and 25–250 bounds |

### `AlarmSlotsTest` (4 tests) and `SparkLabelIndicesTest` (4 tests)

| Test | What it proves |
| :--- | :--- |
| empty alarm set | slot 0 |
| gap in occupied slots | first free slot is reused, not `size` (old overwrite bug) |
| all five slots occupied | `null`, Add is disabled |
| duplicates/out-of-range slots | do not hide a real free slot |
| chart label thinning | indices stay within bounds, first/last retained, no duplicates |
| single-day and empty series | no crash, sensible labels |

## 2. Gradle tasks — results of the verification run (2026-11-09)

Command (from the repository root):

```bash
JAVA_HOME=/home/ganesh/android-toolchain/jdk \
ANDROID_HOME=/home/ganesh/android-toolchain/sdk \
ANDROID_SDK_ROOT=/home/ganesh/android-toolchain/sdk \
./gradlew clean testDebugUnitTest lintDebug assembleDebug --console=plain
```

| Task | Result | Notes |
| :--- | :--- | :--- |
| `testDebugUnitTest` | **PASS** — 75 tests, 0 failures, 0 errors, 0 skipped | `CrrepaProtocolTest` 32, `HealthAggregatorTest` 23, `MigrationSqlTest` 6, `HealthRepositoryTest` 6, `AlarmSlotsTest` 4, `SparkLabelIndicesTest` 4 |
| `lintDebug` | **PASS** — 0 errors, 35 warnings | warnings are unresolved/incremental Compose and dependency notices; no error-severity issue |
| `assembleDebug` | **PASS** — `BUILD SUCCESSFUL in 32s` | `app/build/outputs/apk/debug/app-debug.apk`, 53,351,874 bytes, SHA-256 `b58b55143c77dd83793db2ee0414226b9ad8964924d18bf43757113ae19b1c8d` |
| Room schema export | **PASS** | KSP wrote `app/schemas/com.marvneo.companion.data.db.AppDatabase/4.json` (version 4, 7 entities) |

An earlier run failed at test compilation (`IntRange` passed where `Collection<Int>` was expected in
`AlarmSlotsTest`) and then at one assertion: the test used `260.toByte()`, which wraps to `4`. The
parser bound is now the representable maximum (`60..255` systolic) and the test uses `0xFF`. Both
were real corrections, not test-only workarounds. The clean run reported above is the final green
run and produced the SHA-256 listed in the table.

The APK SHA-256 identifies this exact debug artifact. It is **not** a release-signed build and should
not be distributed as one.

## 3. Blocked tests (hardware)

| Test | State | Reason |
| :--- | :--- | :--- |
| Scan → connect → Ready | BLOCKED | no physical watch |
| Time sync visible on watch | BLOCKED | no physical watch |
| Live HR / SpO2 / BP | BLOCKED | no physical watch |
| Alarm stored and fired | UNOBSERVABLE | firmware has no write acknowledgement |
| Alarm deletion on the watch | BLOCKED | deletion is phone-only in this build; watch-side deletion was not verified |
| Find watch vibrates | UNOBSERVABLE | no reply opcode |
| Battery read value | BLOCKED | no physical watch |
| Reconnect after range loss | BLOCKED | no physical watch |
| Room migration on a device | BLOCKED | no emulator/device; the shipped SQL is covered by `MigrationSqlTest` on the JVM |
| UI on small/standard/large phone | BLOCKED | no emulator/system image or device attached |

See [LIMITATIONS_AND_DEVICE_TESTING.md](LIMITATIONS_AND_DEVICE_TESTING.md) for the manual procedure.

## 4. Static checks that replace some runtime checks

| Check | Method |
| :--- | :--- |
| No fabricated assistant | `grep` for `GEMINI`, `generativelanguage`, `Gemini` outside the explanatory text |
| No network | manifest has no `INTERNET` permission |
| No notification scraping | no `NotificationListenerService` in manifest or source |
| No fake success strings | alarm/find outcomes only `AwaitingAcknowledgement` or `Failed` |
| Database migration additive | all migrations only `CREATE TABLE IF NOT EXISTS` / `ALTER TABLE ... ADD COLUMN` |
| Backup disabled | `android:allowBackup="false"` |
