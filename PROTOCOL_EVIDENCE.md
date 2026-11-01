# Protocol evidence — beatXP Marv Neo (Crrepa / DaFit family)

This document replaces the earlier `HARDWARE_PROTOCOL.md`, which described a Nordic UART
(`6e400001…`) device with a checksum byte, `0x01`/`0x03`/`0x1C` opcodes and a 512-byte MTU. None of
that appears in the reference application. It was an assumption presented as fact.

Everything below is labelled:

| Label | Meaning |
| :--- | :--- |
| **CONFIRMED** | Read directly from bytecode in the reference APK (`/home/ganesh/Documents/Random_things/beatXP.apk`, `com.icomon.dafit`, SDK `com.crrepa.ble`). Class and method are cited. |
| **INFERRED** | Follows from confirmed evidence plus the Android BLE specification, but has not been observed on a physical watch. |
| **UNKNOWN** | No evidence exists. It is not implemented and must not be described as supported. |

Tooling used: `jadx 1.5.1` (Java decompilation) and `baksmali 2.5.2` (Dalvik bytecode, used where
jadx bailed out — notably the 2 690-instruction dispatch method `com.crrepa.l.a#w0`).

---

## 1. Wire frame — CONFIRMED

Builder: `Lio/grpc/w;->Q(I[B)[B` (smali offset 7878 in `smali_classes3/io/grpc/w.smali`).
It copies payload length `n`, allocates `n + 5` bytes, writes:

```
index 0 : 0xFE
index 1 : 0xEA
index 2 : 0x10 if negotiated chunk size == 20, else 0x20
index 3 : total packet length (payload + 5)
index 4 : opcode
index 5…: payload
```

Inbound validator: `com.crrepa.l.a#n0(byte[])` checks `bArr[0] == -2 (0xFE) && bArr[1] == -22 (0xEA)`,
then takes the version from byte 2 and the expected length from byte 3. Split notifications are
accumulated until the declared length is reached; `#w0()` then dispatches on byte 4.

There is **no checksum byte**. Length is a single byte, so a packet is at most 255 bytes.

Reassembler implemented in `CrrepaFrameAssembler.kt` and covered by unit tests.

---

## 2. GATT profile — CONFIRMED

`com.crrepa.p.a` maps a discovered service list to characteristics by UUID substring, and
`com.crrepa.g.e#onServicesDiscovered` decides a device is usable only when `FEE1`, `FEE2` and `FEE3`
all exist.

| Role | UUID | Evidence |
| :--- | :--- | :--- |
| Data service | `0000feea-0000-1000-8000-00805f9b34fb` | `com.crrepa.p.a` (`feea`), `com.crrepa.g.e` |
| Live step notify | `0000fee1-…` | subscribed in `onServicesDiscovered`; parsed by `com.google.android.play.core.appupdate.d#D([B)` |
| Default command write | `0000fee2-…` | `com.crrepa.l.c#Q(int)` case 17/18 mapping |
| Framed packet notify | `0000fee3-…` | feeds `com.crrepa.l.a#n0` |
| Write for command byte `0x01` | `0000fee5-…` | `com.crrepa.l.c#Q` case 19 |
| Write for command byte `0x02` | `0000fee6-…` | `com.crrepa.l.c#Q` |
| Notify | `0000fee7-…` | subscribed in `onServicesDiscovered` |
| Notify | `0000fee8-…` | subscribed in `onServicesDiscovered` |
| Battery | `180f` / `2a19` | standard; read by `queryDeviceBattery` |
| Heart rate | `180d` / `2a37` | standard; `onCharacteristicChanged` routes `2a37` to the HR listener |
| Device info | `180a` / `2a24`, `2a28`, `2a29` | standard model / firmware / manufacturer strings |
| CCCD | `2902` | `com.crrepa.l.f#Q` |

The old app used `FFF0/FFF1/FFF2` and `FEE7/FEB1/FEB2`; the former appear nowhere in the vendor SDK
as the data channel, the latter are single characteristics, not a service/characteristic pair.

---

## 3. Opcodes used by this app — CONFIRMED

All values are the first argument to `io.grpc.w.Q(...)` in `com.crrepa.g.a`.

| Function | Opcode | Payload sent | Reply |
| :--- | :--- | :--- | :--- |
| Time sync (`#syncTime`) | `0x31` (49) | 5 bytes: unix seconds LE32 + UTC offset in whole hours | none |
| Find device (`#findDevice`) | `0x61` (97) | empty | **none defined** |
| Heart rate once (`#startMeasureOnceHeartRate`) | `0x6D` (109) | `{0x00}` start, `{0xFF}` stop | 1 byte completion state |
| SpO2 (`#startMeasureBloodOxygen`) | `0x6B` (107) | `{0x00}` start, `{0xFF}` stop | 1 byte percentage |
| Blood pressure (`#startMeasureBloodPressure`) | `0x69` (105) | `{0,0,0}` start, `{-1,-1,-1}` stop | exactly 3 bytes |
| Alarm write (`#sendAlarm`) | `0x11` (17) | 8-byte alarm slot | none defined |
| Query new alarms (`#queryAllNewAlarm`) | `0xB9` (−71) | `{0x15, 0x04}` | alarm list |

### Heart rate is not the 0x6D reply

`com.crrepa.l.a#K0([B)` passes `payload[0] & 0xFF` to `CRPHeartRateChangeListener#onOnceMeasureComplete`.
That is a **completion state**, not a BPM. Actual beats per minute arrive on the standard Heart Rate
Measurement characteristic `2A37`, where byte 0 is a flags field and bit 0 selects 8-bit or 16-bit
BPM encoding. The old app treated the 0x6D byte as a heart rate; that is why it could display a
constant or absurd value while the sensor was idle.

### SpO2

`com.crrepa.l.a#N0([B)` calls `onBloodOxygen(payload[0])`. This app accepts a value only in
50…100 and logs anything else as implausible rather than storing a sensor error.

### Blood pressure

`com.crrepa.i.b#a([B, listener)` requires `payload.length == 3` and reads
`systolic = payload[1]`, `diastolic = payload[2]`. `payload[0]` is a status byte. This app additionally
rejects implausible pairs, and the UI reports "not supported" when no packet ever arrives.

### Steps

`com.google.android.play.core.appupdate.d#D([B)` requires `length % 3 == 0` and reads little-endian
24-bit values: steps at 0, distance at 3, calories at 6, and — when at least 12 bytes are present —
active minutes at 9. `com.crrepa.n0.a#x([B)` is the 24-bit LE reader (all-`0xFF` means "no value").

---

## 4. Alarm payload — CONFIRMED

Built by `Lio/grpc/w;->S(CRPAlarmInfo)[B`, eight bytes:

```
[0] slot id            0…4
[1] enabled            0 / 1
[2] mode               0 = single occurrence
                       0x7F = every day
                       2 = custom repeat
[3] hour
[4] minute
[5..6] date code, little endian, only for single occurrence:
       ((year - 2015) << 12) | (month << 8) | dayOfMonth
[7] repeat mask
```

Repeat mask bits (`CRPAlarmInfo`): bit 0 Sunday, bit 1 Monday … bit 6 Saturday; `0x7F` = every day.

`#sendNewAlarm` wraps this in a 10-byte payload `{5, 0, <8 alarm bytes>}` before framing.
The simpler `#sendAlarm` path used here sends the 8-byte payload directly to `0x11`, which is what the
reference application's `CrpBleConnectionImpl` uses for alarm writes.

### No write acknowledgement — CONFIRMED ABSENCE

`com.crrepa.l.a#w0` has no case for `0x11` and no case for `0x61`. The vendor SDK's alarm callback is
populated by `queryAllNewAlarm` (`0xB9`), not by the write. Therefore:

* an alarm write can be confirmed as **written to the GATT characteristic** (Android write callback);
* it **cannot** be confirmed as stored by the watch;
* `findDevice` is fire-and-forget.

The app reports both as `AwaitingAcknowledgement`, and alarm rows can never reach `SYNCED` from a
write alone. The earlier app's "synced" badge was unearned.

---

## 5. Connection sequence — CONFIRMED

From `com.crrepa.g.e`:

1. `connectGatt` → on `STATE_CONNECTED` the SDK waits **300 ms** (`com.crrepa.f.a(…, 300L)`) before
   `discoverServices()`.
2. On discovery, requires `FEE1` + `FEE2` + `FEE3`; otherwise the device is rejected.
3. Subscribes to `FEE3`, `2A37`, `FEE7`, `FEE8`, `FEE1` by writing CCCD `2902`.
4. MTU: on `onMtuChanged`, `chunk = (mtu - 3); chunk -= chunk % 4`. The default chunk is 20, which is
   why the frame version is `0x10` before a larger MTU is negotiated.
5. Commands are written to `FEE2` (default), with `FEE5`/`FEE6` reserved for command bytes `0x01`/`0x02`.

The new manager follows this order and serialises every GATT request behind one mutex with a
callback-backed `CompletableDeferred`, because Android permits only one outstanding GATT operation.

---

## 6. Assistant / voice — UNSUPPORTED, with evidence

`CRPChatChangeListener` exposes only:

* `onActivateRecording`
* `onStartRecording(byte[])`
* `onStopRecording`
* `onAskQuestion`
* `onChatCancel`

These are phone→watch recording controls. There is **no callback that delivers a recognised
transcript, an answer, or chat history from watch to phone**. The old app's Gemini integration
therefore had no data source at all; its "conversation history" was fabricated.

Consequences in this build:

* no Gemini/API key, no network permission, no text generation;
* the Assistant screen lists only messages that arrived over a real channel (currently none can);
* the empty state explains the limitation instead of inventing examples.

---

## 7. Things deliberately **not** claimed

| Feature | Status | Reason |
| :--- | :--- | :--- |
| Alarm stored on watch | UNKNOWN | no acknowledgement opcode |
| Find-watch ringing | UNKNOWN | no reply opcode; vibration is not observable over BLE |
| Blood pressure on this specific unit | UNKNOWN until a real packet arrives | protocol supports the request; hardware may not answer |
| Sleep, stress, HRV history | UNKNOWN | not implemented; vendor history sync is a multi-step state machine not verified here |
| Watch faces / app install / firmware update | UNKNOWN | vendor OTA paths are separate; the old "success" messages were fabricated |
| Notification mirroring | NOT IMPLEMENTED | requires a notification listener and vendor message opcodes not verified here |

## 8. How to extend this document

Add a row only after the behaviour is observed on a physical Marv Neo with the diagnostics log from
More → Diagnostics. Until then, label it UNKNOWN.
