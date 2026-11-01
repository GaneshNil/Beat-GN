# Reference APK vs previous companion vs this rewrite

Reference: `/home/ganesh/Documents/Random_things/beatXP.apk`
Package `com.icomon.dafit`, BLE SDK `com.crrepa.ble` (entry `CrpBleClientImpl`,
connection implementation `com.icomon.dafit.sdkintegration.crp.CrpBleConnectionImpl`).
It is the vendor application for the same hardware family, so it is the strongest available evidence
for what the watch can actually do.

This is a capability comparison, not a byte-for-byte feature clone. The rewrite implements a
deliberately smaller subset, and every item it does not implement is labelled as such.

## 1. Architecture

| Concern | Reference APK | Previous `com.marvneo.companion` | This build |
| :--- | :--- | :--- | :--- |
| Transport | Crrepa SDK over `FEEA/FEE1-FEE3` | Claimed Nordic UART `6e400001…` / `FFF0…`; actual behaviour unverified | Direct GATT over `FEEA/FEE1-FEE3` with the SDK's exact framing |
| Connection truth | SDK state machine, callbacks | Local boolean set after a write | Explicit state machine; `Ready` only after a verified read |
| Command serialisation | SDK queue | Parallel calls | One mutex, callback-backed operations |
| Framing | `FE EA`, version, total length, opcode | Invented header + checksum | `FE EA`, version, total length, opcode (no checksum) |
| Chunking | `(mtu-3) - ((mtu-3) % 4)` | Not implemented | Same rule, `0x10`/`0x20` version selection |
| Persistence | Room (app-specific schema) | Room v3 | Room v4, additive migration |
| Voice/AI | No transcript channel exposed by SDK | Gemini-generated fake history | Removed entirely; honest empty state |

## 2. Feature truth table

| Feature | Reference APK | Previous app claimed | Reality | This build |
| :--- | :--- | :--- | :--- | :--- |
| Connect | Yes | "Connected • BLE Active" from a write | Not proven | `Ready` only after battery read-back; otherwise `Unstable` |
| Time sync | Yes (`0x31`) | Yes | Confirmed | Yes (`0x31`, LE32 + tz hours) |
| Battery | Standard `2A19` read | Shown, possibly defaulted | Confirmed | Read on connect and every 30 s; `—` until read |
| Heart rate | `2A37` + `0x6D` start/stop | 0x6D byte treated as BPM | Wrong | `2A37` only; 0x6D = state |
| SpO2 | `0x6B` | Yes, with seeded values | Partly confirmed | `0x6B`; value only from a real packet |
| Blood pressure | `0x69`, 3-byte reply | Presented as standard | Hardware-dependent | Probe + honest "never received" state |
| Steps | `FEE1` 3-byte triplets | Different 4×uint32 layout | Wrong | SDK layout, tested |
| Sleep | Vendor history sync | Claimed | Not verified | Not implemented (labelled) |
| Alarms | `0x11` write + `0xB9` read | "Synced" badge | No write ack | Write, stay `PENDING`, explained |
| Find watch | `0x61`, no reply | "Success" | No ack | "Sent, unconfirmed" |
| Notifications | Vendor opcodes + listener | Scraped all notifications | Privacy problem, unverified opcodes | Removed |
| Watch faces / OTA | Vendor OTA services | Fake progress + success | Not implemented | Removed |
| Assistant | SDK recording controls only | Gemini answers, fake history | No source | No key, no network, empty state |
| Reports | Vendor app history | Rolling 24 h labelled "today" | Wrong boundaries | Local midnight / 7 calendar days / month, tested |
| Privacy | Vendor analytics present | Claimed zero telemetry but had AI/network paths | Contradiction | No INTERNET permission, no analytics, no key |

## 3. What was taken from the reference APK

* UUIDs and the requirement that `FEE1`, `FEE2`, `FEE3` all exist.
* The exact frame layout and reassembly rule.
* Opcode values and payload shapes for time, HR, SpO2, BP, alarm write, alarm query, find device.
* The 8-byte alarm layout and repeat-mask bit order.
* The MTU chunk formula and the 300 ms settle delay before discovery.
* The connection sequence (discover → validate → subscribe → use).
* The negative evidence: no `0x11` or `0x61` acknowledgement path, and no watch→phone transcript
  callback.

## 4. What was **not** taken

* Vendor analytics / tracking.
* Vendor account, login and cloud sync.
* OTA and watch-face pipelines (their framing is large and was not verified; implementing it from a
  partial reading would repeat the previous mistake).
* Notification mirroring, camera shutter, music control, contacts, menstrual cycle, stress, sleep
  history — all present in the vendor app, none verified here, none exposed as working.

## 5. Package identity

The rewrite keeps `com.marvneo.companion` so it installs over the previous build and exercises the
Room migration. It is not a re-signed copy of `com.icomon.dafit`; no vendor code or resources were
copied.
