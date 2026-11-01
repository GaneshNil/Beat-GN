# Limitations, blocked verification and physical-device test plan

## 1. What could not be verified here

| Item | Why | Status |
| :--- | :--- | :--- |
| Any Bluetooth behaviour on a Marv Neo | No watch is connected to this machine | **BLOCKED** |
| UI rendering on a phone (clipping, insets, dark/light) | No emulator or system image installed; no device attached | **BLOCKED** |
| Alarm actually stored/fired by the watch | Firmware sends no acknowledgement for `0x11` | **UNOBSERVABLE** |
| Find-watch actually vibrating | No reply opcode for `0x61` | **UNOBSERVABLE** |
| Blood pressure on this specific unit | Hardware may not implement `0x69` | **UNKNOWN until tested** |
| Room migration on a real v3 database | No v3 database fixture was captured | **BLOCKED** (migration is additive SQL and unit-reviewable) |

Nothing above is reported as passing.

## 2. Design limitations that are intentional

1. **Alarms stay `PENDING`.** The protocol gives no write acknowledgement. Marking them synced would be
   a lie, so the UI explains why they are pending.
2. **Find watch says "sent, unconfirmed".** Same reason.
3. **No assistant transcripts.** The vendor SDK exposes phone-side recording callbacks only. There is
   no watch→phone transcript channel; the screen stays empty rather than inventing history.
4. **No notification mirroring.** The previous implementation scraped every notification on the phone,
   which is a privacy hazard, and used message opcodes that were never verified. Both are gone.
5. **No watch faces, app install or firmware update.** The previous "success" was fabricated.
6. **Blood pressure is opt-in evidence.** The request is sent only when the user taps "BP probe"; the
   card says "never received" unless a real 3-byte reply arrived.
7. **Sleep and stress are not shown.** History sync was not verified, so no screen pretends to have it.

## 3. Physical-device test plan

Prerequisites: Android phone (API 30+), the watch charged and worn, the app installed from
`app/build/outputs/apk/debug/app-debug.apk`, Bluetooth on, permissions granted.

For each test, record the result as PASS / FAIL / NOT OBSERVED. "NOT OBSERVED" is a valid result.

### 3.1 Permissions and service

1. Launch the app. Grant Nearby Devices and Notifications.
2. Expected: a silent foreground notification appears with the current link state.
3. Turn Bluetooth off from quick settings.
4. Expected: app state becomes "Bluetooth off"; notification text changes. Turn Bluetooth on again.

### 3.2 Scan and connect

1. Open **Watch** → **Scan**.
2. Expected: devices appear only if actually heard; the watch is tagged "likely watch" if its name or
   class matches. No device is listed from memory.
3. Tap the watch row.
4. Expected sequence in the status/description: Connecting → Discovering → Subscribing → Verifying.
5. **PASS only if** it reaches "Ready" *and* the Device card shows a real battery percentage.
6. If it stops at "Unstable", that is a correct honest result: the watch did not answer a read.
7. Open **More → Diagnostics** and keep the log for any bug report.

### 3.3 Time sync

1. While Ready, compare the watch clock with the phone.
2. Expected: within a few seconds after connecting, the watch shows the same local time.
3. Diagnostics should contain `TX FE EA 10 0A 31 …`.

### 3.4 Heart rate

1. **Health → Pulse**. Keep still.
2. Expected: "Measuring…" appears; when a real value arrives it is stored and shown with a timestamp.
3. If nothing arrives within 40 s, the UI must say it timed out. A timeout is a PASS for honesty, a
   FAIL only if a value was displayed without a reading.
4. Check **Health → Readings** for a new row.

### 3.5 SpO2

1. **Health → Oxygen**. Keep still.
2. Expected: a percentage only after the watch replies on `0x6B`; otherwise a timeout message.
3. Values outside 50–100 must be discarded (check diagnostics).

### 3.6 Blood pressure

1. **Health → BP probe**.
2. If the watch answers `0x69` with 3 bytes, a systolic/diastolic pair appears and the card changes to
   "N readings in this period".
3. If it does not answer within 30 s, the card must say no BP data has ever been received. Do not
   treat that as an app failure; it is the honest hardware answer.

### 3.7 Alarms

1. **Watch → +**, set a time one or two minutes ahead, enable it, Save.
2. Expected: the row appears with a **pending** badge. It must never say "synced".
3. Wait for the time. Record whether the watch vibrates.
4. Reopen the app. The row is still pending, because the app cannot read back what the firmware stored.
5. Optional: use **More → Diagnostics** to confirm the `0x11` write was sent.

### 3.8 Find watch

1. **Watch → Vibrate watch**.
2. Expected: the button reports "sent to the watch … not reported as success". Record whether the watch
   vibrates. The app must not claim confirmation either way.

### 3.9 Reports

1. Take a measurement, then open **Health**.
2. Switch Today / 7 days / Month.
3. Expected: each window states its boundary; days without data show empty bars, not zero-height
   "data"; averages match the listed readings.
4. Cross-check one reading timestamp against the wall clock and time zone.

### 3.10 Reconnect and background

1. Walk out of range for ~30 s, come back.
2. Expected: Reconnecting (n/5), then Ready after a verified exchange. If it exhausts attempts, it says
   so and stays disconnected.
3. Background the app for 10 minutes with the watch connected.
4. Expected: the notification continues to mirror the real state. Battery optimisation may still kill
   the link on aggressive OEMs; use the button in Watch → "Allow background connection".

### 3.11 Data integrity

1. Compare the count of readings in the app with the number of measurements you actually took
   (allowing for the 3–5 s duplicate filter).
2. Delete an alarm; confirm it disappears after restart.
3. Rotate/restart the phone; confirm no duplicate rows appear.

## 4. Reporting a bug

Include: phone model, Android version, watch name/address suffix, the Diagnostics log (More →
Diagnostics), and which step above failed. Without the diagnostics log a BLE bug is not diagnosable.
