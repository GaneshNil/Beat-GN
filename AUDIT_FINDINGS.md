# Beat-GN — source audit findings (Phase 1)

Audit date: 2026-11-08  
Repository: `https://github.com/GaneshNil/Beat-GN`  
Checkout: `2c7881f180ad46501b358b48932551e1bd3ed2de` (`main`)  
Branch for this work: `audit/fix-p0-p1`

Classification used below:

* **CONFIRMED BUG** — proven by reading current source and/or the Android API contract.
* **VERIFIED LIMITATION** — a protocol/product constraint, not an app defect.
* **UNVERIFIED** — needs a physical watch, emulator, or an instrumented run.
* **NOT PRESENT / ALREADY FIXED** — the previous defect is absent from current source.
* **NEW FINDING** — discovered during this audit.

The companion PDF is documentation-backed and was treated as a handoff, not as proof. Every
row below was checked against source.

---

## P0 — data loss / upgrade blocker

### F-01 · `MIGRATION_1_2` is empty although v2 added `voice_messages` · CONFIRMED BUG

* **Evidence**
  * `app/src/main/java/com/marvneo/companion/data/db/AppDatabase.kt:44-48` — `MIGRATION_1_2`
    contains only a comment.
  * `AppDatabase.kt:94-95` — `MIGRATION_3_4` executes
    `ALTER TABLE voice_messages ADD COLUMN sessionId TEXT`.
  * Git history `1192ffa` shows database version 2 added the `voice_messages` entity/table;
    git history `56a5308` shows version 1 had four entities and no `voice_messages`.
  * The current migration chain therefore runs v1 → v2 without creating the table, then v2 → v3
    (which does not create it either), then v3 → v4, where the `ALTER TABLE` fails.
* **User impact** — a user upgrading from the v1 schema crashes at Room open/validation; their
  local history is inaccessible. This is a data-availability blocker, not a cosmetic issue.
* **Reproduction** — create a v1 database (four tables, `PRAGMA user_version=1`), open the current
  Room database, observe the migration validation failure at `voice_messages`.
* **Fix** — make `MIGRATION_1_2` create `voice_messages` with the v2 schema
  (`id`, `timestamp`, `sender`, `text`, `toolExecuted`, `status`), additively and idempotently.
* **Tests required** — a v1 fixture with representative readings; migrate to v4; assert every old
  row survives and the new columns/tables exist.

---

## P1 — core reliability and data integrity

### F-02 · Legacy `onCharacteristicRead` callback is not overridden · CONFIRMED BUG

* **Evidence** — `MarvNeoBleManager.kt:239-252` overrides only
  `onCharacteristicRead(BluetoothGatt, BluetoothGattCharacteristic, byte[], int)`, which was added
  in API 33. The project's `minSdk` is 30 (`app/build.gradle.kts:13`).
* **Mechanism** — on API 30–32 Android invokes
  `onCharacteristicRead(BluetoothGatt, BluetoothGattCharacteristic, int)`. The class does not
  override it, so `completeGattOp(true/false)` is never called for any read. `verifyLink()`
  (`MarvNeoBleManager.kt:728-737`) waits for a battery read; it always times out. `Ready` is never
  published on Android 11/12, and device-information reads also time out.
* **User impact** — the app cannot reach its only honest "connected" state on a large part of the
  supported API range. All measurement buttons remain disabled.
* **Fix** — override the deprecated 3-argument callback, read `characteristic.value`, and route it
  through the same `handleReadValue` + `completeGattOp` path with a stale-GATT guard.
* **Tests required** — cannot be proven by a pure JVM test; add a manual API 30/31/32 device or
  emulator test and record it as such.

### F-03 · Reconnect limit is not enforced · CONFIRMED BUG

* **Evidence** — `MarvNeoBleManager.kt:492` resets `reconnectAttempt = 0` on every `connect()`.
  `scheduleReconnect()` calls `connect(address)` at `MarvNeoBleManager.kt:823-826`, so each retry
  resets the counter before the next failure. `MAX_RECONNECT_ATTEMPTS = 5` is never reached.
* **User impact** — an out-of-range or powered-off watch causes an unbounded reconnect loop
  (with diagnostics growth and battery use), contradicting the documented bounded policy.
* **Fix** — distinguish user/auto connect from a scheduled reconnect; only user/auto connect resets
  the counter. Reset on `publishReady()`.

### F-04 · Stale GATT callbacks can complete the current operation · CONFIRMED BUG

* **Evidence** — only `onConnectionStateChange` checks `gatt !== this.gatt`
  (`MarvNeoBleManager.kt:183-186`). `onMtuChanged`, `onServicesDiscovered`, `onDescriptorWrite`,
  `onCharacteristicWrite`, `onCharacteristicRead` and `onCharacteristicChanged`
  (`MarvNeoBleManager.kt:210-269`) act on any GATT object.
* **User impact** — a late callback from an old connection can release the single-operation mutex
  for the wrong request, producing phantom success/timeouts and command loss (the GATT 133 class of
  failure the project claims to have removed).
* **Fix** — central stale-GATT guard applied to every callback; close a stale GATT and ignore it.

### F-05 · All-`0xFF` step fields are accepted as real values · CONFIRMED BUG

* **Evidence** — `CrrepaProtocol.parseSteps` (`CrrepaProtocol.kt:278-292`) decodes every 24-bit
  field without the documented "all-`0xFF` means no value" rule
  (`PROTOCOL_EVIDENCE.md:107`). `MarvNeoBleManager.onSteps` (`MarvNeoBleManager.kt:1026-1044`)
  stores the result.
* **User impact** — a "no value" notification displays and persists `16,777,215` steps (or the same
  for distance/calories), i.e. corrupt data accepted as a health reading.
* **Fix** — decode each field as nullable; `0xFFFFFF` becomes `null`; do not persist a step record
  unless steps, distance and calories are present; never show a fabricated zero for an absent field.
* **Related** — `parseSteps` currently rejects 3- and 6-byte payloads
  (`CrrepaProtocol.kt:279`), although the device-test matrix asks for 3/6/9/12-byte coverage. A
  3-byte payload carries a real step count and should not be discarded.

### F-06 · One shared timeout job aborts other measurements · CONFIRMED BUG

* **Evidence** — a single `measurementTimeoutJob` (`MarvNeoBleManager.kt:111`) is cancelled and
  replaced by every `armMeasurementTimeout`/`probeBloodPressure` call
  (`MarvNeoBleManager.kt:1138-1159`); `completeMeasurement` cancels it for any measurement
  (`MarvNeoBleManager.kt:1049-1055`).
* **User impact** — start Pulse, then Oxygen: the HR timeout is cancelled and, if no HR value
  arrives, the UI stays at "Measuring…" indefinitely. The reverse order has the same effect.
* **Fix** — one timeout job per measurement state flow; cancel only the job belonging to the flow
  being completed/reset.

### F-07 · Alarm slot allocation can overwrite an existing alarm · CONFIRMED BUG

* **Evidence** — `WatchScreen.kt:227` calls `emptyAlarm(alarms.size)`;
  `WatchScreen.kt:450-462` picks the first slot `>= usedSlots`. With slots `{0, 2, 3}`, size is 3,
  so the next editor is slot 3 — an existing alarm. Saving replaces it.
* **User impact** — silent loss of an alarm configuration.
* **Fix** — allocate the first unused slot in `0..4`; disable "Add" when all five are used.

### F-08 · Explicit disconnect is undone by the service · CONFIRMED BUG

* **Evidence** — `disconnect()` sets `userDisconnected = true` (`MarvNeoBleManager.kt:527-538`),
  but `autoConnect()` never checks it (`MarvNeoBleManager.kt:427-450`). `MainActivity.onStart`
  starts the service (`MainActivity.kt:179-185`); `WatchCompanionService.onStartCommand` calls
  `autoConnect()` (`WatchCompanionService.kt:80-88`).
* **User impact** — after tapping "Disconnect", returning to the app can immediately reconnect,
  contrary to the user's explicit action.
* **Fix** — `autoConnect` respects the explicit-disconnect flag unless invoked by a user action
  (the Watch screen's Reconnect button), and `startScan` clears the flag.

---

## P2 — truthfulness, lifecycle, privacy, UI

### F-09 · Live telemetry is not cleared on disconnect · CONFIRMED BUG

* **Evidence** — `heartRateFlow`, `spO2Flow`, `stepsFlow`, `batteryFlow`, etc.
  (`MarvNeoBleManager.kt:134-143`) are only reset at the start of `connect` for battery. On link
  loss/disconnect they retain old values; `HomeScreen.kt:223-232` renders "Live pulse … just now"
  whenever `heartRate > 0`, even with no link.
* **User impact** — stale measurements are presented as live.
* **Fix** — clear live flows and device-information values in the centralized GATT cleanup; keep
  historical records in Room and keep `bloodPressureEverReported` as evidence. Gate "Live pulse" on
  `Ready`.

### F-10 · Blood-pressure "never received" is false after a restart · CONFIRMED BUG

* **Evidence** — `bloodPressureEverReported` is an in-memory `MutableStateFlow`
  (`MarvNeoBleManager.kt:151`); `HealthScreen.kt:324-331` uses it to claim no BP data has ever
  arrived. Room may still contain BP rows from an earlier session.
* **Fix** — derive the "ever" state from a DAO `EXISTS` flow and OR it with the in-session flag.

### F-11 · Blood-pressure writes bypass duplicate filtering · CONFIRMED BUG

* **Evidence** — `HealthRepository.recordHeartRate` and `recordBloodOxygen`
  (`HealthRepository.kt:31-45`) check a ±5 s window; `recordBloodPressure`
  (`HealthRepository.kt:47-57`) does not.
* **Fix** — add the equivalent DAO count query and duplicate check.

### F-12 · Blood-pressure parser accepts implausible pairs · CONFIRMED BUG

* **Evidence** — `CrrepaProtocol.parseBloodPressure` (`CrrepaProtocol.kt:248-254`) only rejects
  zero. Range/systolic-above-diastolic checks live in the manager
  (`MarvNeoBleManager.kt:974-976`), so any other caller can accept bad data.
* **Fix** — enforce physiological bounds and `systolic > diastolic` in the parser, with tests.
  Systolic is capped at 255 because it is a single unsigned byte; the earlier `260` upper bound
  was unreachable (the first version of the test passed `260.toByte()`, which wraps to `4`).

### F-13 · A failed alarm write remains "pending" instead of "failed" · CONFIRMED BUG

* **Evidence** — `WatchScreen.kt:260-269` and `315-321` ignore the returned `CommandOutcome`.
  `AlarmSyncState.FAILED` exists but is never written.
* **Fix** — map `CommandOutcome.Failed` to `FAILED`; keep `PENDING` for transport success because
  firmware still cannot acknowledge storage.

### F-14 · A zero repeat mask is labelled "Once" but encoded as "custom repeat, no days" · CONFIRMED BUG

* **Evidence** — `WatchScreen.kt:464-466` maps mask `0` to "Once"; `CrrepaProtocol.alarm`
  (`CrrepaProtocol.kt:186-190`) maps it to mode `0x02` (custom repeat) because no one-off date is
  passed. A dated single occurrence needs mode `0x00` plus a date code.
* **Fix** — stop claiming "Once"; explain that a no-days alarm may never fire and that this build
  does not send a dated one-off. Do not invent a date.

### F-15 · Deleting an alarm only deletes the phone row · CONFIRMED LIMITATION (UI copy)

* **Evidence** — `WatchScreen.kt:257-259` deletes from Room only. There is no documented delete
  opcode; a write with `enabled = false` is the available signal but is not sent.
* **User impact** — the watch may keep alerting after the user deletes the alarm.
* **Fix (safe part)** — state this explicitly in the UI; optionally send a disabled write when
  Ready, still as unconfirmed.

### F-16 · `allowBackup="true"` contradicts the privacy statement · CONFIRMED BUG

* **Evidence** — `AndroidManifest.xml:30` enables Android auto-backup; `MoreScreen.kt:282` says
  "Nothing. There is no analytics, no crash reporting, no login and no cloud service." Health data
  in the Room database can be included in platform backup.
* **Fix** — disable backup for this app (or add explicit data-extraction rules that exclude the
  database) so the statement is true.

### F-17 · Foreground-service start failure is swallowed · CONFIRMED BUG

* **Evidence** — `MainActivity.kt:211-216` wraps `startForegroundService` in `runCatching` with no
  logging or user-visible result; `requestIgnoreBatteryOptimization`/`openAppDetails` do the same
  (`MainActivity.kt:218-241`).
* **Fix** — log the failure safely (no health data), and keep the app usable with the honest
  "no live link" state.

### F-18 · "Stop scan" leaves the state at Scanning · CONFIRMED BUG

* **Evidence** — `stopScan()` (`MarvNeoBleManager.kt:369-376`) clears `_isScanning` but never
  changes `ConnectionState.Scanning`; the timeout job that would have changed it is cancelled.
* **Fix** — transition to `Disconnected` when scanning is stopped by the user/scan failure, unless a
  connection attempt has already taken over.

### F-19 · Alarm day chips can clip on compact screens/large fonts · CONFIRMED UI RISK

* **Evidence** — seven `FilterChip`s in a plain `Row` (`WatchScreen.kt:412-420`) with no scroll or
  wrap. At 320 dp / large font scale the row exceeds the content width.
* **Fix** — make the chip row horizontally scrollable (or wrap with FlowRow).

### F-20 · Month chart labels overlap · CONFIRMED UI RISK

* **Evidence** — `SparkBars` (`Metrics.kt:93-126`) renders one label per bar; `HealthScreen` passes
  up to 31 daily points (`HealthScreen.kt:207-211`). At 31 bars the labels collide.
* **Fix** — thin labels (e.g. first/last/every Nth) when the bucket count is large; keep all bars.

### F-21 · "More" menu rows are not fully clickable · CONFIRMED UX ISSUE

* **Evidence** — `MenuRow` (`MoreScreen.kt:125-142`) attaches the click only to the chevron
  `IconButton`; the text/row area is inert.
* **Fix** — make the whole row clickable and keep a non-interactive chevron.

### F-22 · Room schemas are not exported · CONFIRMED GAP

* **Evidence** — `AppDatabase.kt:30` sets `exportSchema = false`; there is no `schemas/`
  directory. The project's own process (and the handoff prompt) requires exported schemas for
  migration review.
* **Fix** — `exportSchema = true`, configure `room.schemaLocation`, commit the generated v4 schema.

### F-23 · No migration test fixture exists · CONFIRMED GAP

* **Evidence** — `TEST_MATRIX.md:90-101` lists migration/device work as blocked; there is no
  `androidTest` migration test and no v3 fixture.
* **Fix** — add a runnable JVM SQL-level fixture test for the additive statements (v1→v4), and
  document that Room's instrumented migration test still needs a device/emulator.

### F-24 · Report windows end at "now", not the next calendar boundary · SPEC DEVIATION

* **Evidence** — `ReportWindows.of` (`HealthAggregator.kt:31-55`) uses `nowMillis` as `endMillis`
  for all periods; `TEST_MATRIX`/the handoff ask for "startOfDay to next startOfDay". For readings
  this is equivalent unless a future-dated row exists; the window's stated boundary is still not
  the documented one.
* **Classification** — not a data-corruption bug. It is a precise-boundary deviation. It is left
  unchanged in this pass to avoid shifting `aggregate(nowMillis = window.endMillis)` into the next
  day; tests for DST/time-zone/rollover are added instead. See "Open decisions".

### F-25 · `HealthRepository` uses the system clock directly · NEW FINDING

* **Evidence** — `HealthRepository.kt:66` calls `System.currentTimeMillis()` inside `report`.
  `HealthAggregator` accepts a `ZoneId`, but the repository does not inject a clock or zone.
* **Fix** — inject `clock: () -> Long` and `zone: ZoneId` with production defaults; pass both to
  `ReportWindows`/`HealthAggregator`. Add DST/time-zone tests at the pure-logic layer.

### F-26 · Diagnostics logs the full device address · NEW FINDING (privacy)

* **Evidence** — `MarvNeoBleManager.kt:505` logs `Connecting to $name ($address)`. The diagnostics
  list is bounded to 250 entries (`DIAGNOSTIC_LIMIT`) and is user-viewed only, but a full MAC is a
  persistent identifier.
* **Fix** — redact the address in diagnostics (show a short suffix); keep the full address only on
  the user-facing Device information page.

### F-27 · `notifyConfirmed` is a non-thread-safe mutable set · NEW FINDING

* **Evidence** — `MarvNeoBleManager.kt:682` uses `mutableSetOf`; it is mutated from coroutines and
  GATT callbacks.
* **Fix** — use a concurrent set.

### F-28 · Unstable state has no recovery path after the heartbeat trips · NEW FINDING

* **Evidence** — `startHeartbeat` (`MarvNeoBleManager.kt:1217-1236`) sets `Unstable` after 75 s of
  silence, then exits on the next iteration because the state is no longer `Ready`. There is no
  verification retry like the initial handshake has.
* **Fix** — keep the heartbeat alive in `Unstable` and retry `verifyLink()` at a bounded cadence;
  publish `Ready` only after a real read succeeds.

### F-29 · Measurement timeouts do not stop the watch measurement · SPEC DEVIATION

* **Evidence** — timeout handlers only change local state (`MarvNeoBleManager.kt:1148-1159`);
  documented stop commands (`0x6D`/`0x6B`/`0x69` with `0xFF` payloads) are not sent.
* **Fix** — on timeout, send the matching stop command if the link is Ready; ignore its result for
  the UI state.

---

## P3 — maintenance and coverage

| ID | Finding | Evidence |
| :-- | :--- | :--- |
| F-30 | Step table grows on every changed count; not used by reports | `MarvNeoBleManager.kt:1026-1044`; `HealthDao.kt:22-26` |
| F-31 | `isChargingFlow`, `AlarmModel`, `VoiceMessage` are unused model/state surface | `MarvNeoBleManager.kt:142`; `DeviceState.kt:65-103` |
| F-32 | `AlarmModel` comment says bit0 = Monday; protocol says bit0 = Sunday | `DeviceState.kt:69` vs `CrrepaProtocol.kt:213-221` |
| F-33 | `SparkBars` renders a 2 dp bar for null days; docs say "empty", which may read as data | `Metrics.kt:101-116` |
| F-34 | Protocol tests omit all-`0xFF` steps, 3/6-byte payloads, implausible BP, malformed HR flags, assembler version/buffer cases | `CrrepaProtocolTest.kt` |
| F-35 | Report tests omit DST, time-zone change, year rollover, empty 7-day set, BP dedupe | `HealthAggregatorTest.kt` |
| F-36 | Dependencies are the 2024 line (Kotlin 1.9.24, AGP 8.4.1, Compose BOM 2024.05.00, Room 2.6.1); no advisory review | `gradle/libs.versions.toml` |
| F-37 | Frame assembler accepts any version byte; a false `FE EA` prefix with a large declared length can swallow following bytes until 255 are buffered. No checksum exists, so full validation is impossible. | `CrrepaFrameAssembler.kt:43-53` |

---

## Already fixed / not present (verified in current source)

* `Ready` is not set from `connectGatt()`/write enqueue; it requires discovery, CCCD writes and a
  battery read (`MarvNeoBleManager.kt:627-680`).
* `0x6D` is treated as a completion state, not BPM; BPM comes from `2A37`
  (`MarvNeoBleManager.kt:992-1002`, `1011-1024`).
* No Gemini/API-key/network path; manifest has no `INTERNET` permission.
* No `NotificationListenerService`, microphone trigger, watch-face/OTA fake-success path.
* SpO2 values outside 50..100 are rejected (`CrrepaProtocol.kt:237-242`).
* Alarms are never marked `SYNCED` from a write; find-watch reports
  `AwaitingAcknowledgement` (`MarvNeoBleManager.kt:1169-1213`).
* The v2 `fallbackToDestructiveMigration()` is gone from current `AppDatabase`; only additive
  migrations are registered (`AppDatabase.kt:99-111`).

## Intentionally unsupported (not bugs)

Assistant transcripts, notification mirroring, microphone capture, watch faces/app install/firmware
update, alarm acknowledgement, find-watch acknowledgement, blood-pressure capability, sleep and
stress history. See `LIMITATIONS_AND_DEVICE_TESTING.md`.

---

## Open decisions / hardware blockers

1. **Report boundary semantics** — the handoff asks for `startOfDay..nextStartOfDay`; current code
   uses `..now`. Changing it requires reworking `aggregate(nowMillis = window.endMillis)` so it
   does not roll into the next day. Left unchanged pending owner confirmation.
2. **Alarm deletion on the watch** — no documented delete opcode. A disabled write is the only
   available signal; not implemented without device evidence.
3. **API 30–32 read callback** — the source fix is clear, but PASS/FAIL requires a device/emulator
   at those API levels.
4. **Physical watch acceptance** — scan/connect/Ready, battery, time sync, HR, SpO2, BP, alarm
   firing, find vibration, reconnect, screen-off background. All remain NOT OBSERVED.

---

## Verification run (2026-11-09)

Command:

```bash
JAVA_HOME=/home/ganesh/android-toolchain/jdk \
ANDROID_HOME=/home/ganesh/android-toolchain/sdk \
ANDROID_SDK_ROOT=/home/ganesh/android-toolchain/sdk \
./gradlew clean testDebugUnitTest lintDebug assembleDebug --console=plain
```

* `testDebugUnitTest` — **PASS**, 75 tests, 0 failures, 0 errors, 0 skipped.
* `lintDebug` — **PASS**, 0 errors, 35 warnings.
* `assembleDebug` — **PASS**; APK SHA-256 in `TEST_MATRIX.md` and `PROJECT_STATUS.md`.
* Room schema `app/schemas/com.marvneo.companion.data.db.AppDatabase/4.json` generated by KSP.

Nothing Bluetooth-, alarm- or rendering-dependent is claimed as passing; those remain BLOCKED or
UNOBSERVABLE without the watch and an emulator/device.
