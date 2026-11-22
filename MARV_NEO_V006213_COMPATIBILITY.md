# beatXP Marv Neo V006213 compatibility

Updated 22 November 2026 for Beat-GN 1.1.3.

## Target and evidence

| Field | Value |
| --- | --- |
| Target | beatXP Marv Neo, firmware V006213, Android 11 and later |
| Known address suffix | `5D:D9`. Compare the address locally if the watch exposes it. Scan and selection accept other addresses, including a changed address after a rescan. |
| Product documentation | The manufacturer’s BXSM2001 instruction manual describes DaFit setup, heart-rate tracking, activity features and a separate Bluetooth calling pairing flow. Match the exact unit using the watch’s About screen. [Manufacturer manual, hosted by ManualsLib](https://www.manualslib.com/manual/4141784/Beatxp-Marv-Neo-Bxsm2001.html) |
| Protocol documentation | [PROTOCOL_EVIDENCE.md](PROTOCOL_EVIDENCE.md) records prior analysis of DaFit package `com.icomon.dafit` and SDK `com.crrepa.ble`. The repository provides method citations and wire layouts. |
| Firmware-specific evidence | The target firmware and address were supplied by the owner. The owner's Android 16 screenshot shows Home rendered and service discovery for the selected device returned no FEEA. The screenshot does not identify the selected Bluetooth endpoint or show its full service inventory. No V006213 GATT inventory, HCI capture, watch response or firmware image was available in this audit. The vendor APK was absent at the historical path and no local copy was found. The method citations could be checked against Beat-GN and its history, but the APK could not be independently re-decompiled. |
| Physical availability | ADB returned an empty device list. A 25-second passive identification check using the host BLE adapter found no candidate matching the target address suffix or model name. GitHub's Android 16 emulator does not exercise the physical watch. All end-to-end watch scenarios are **BLOCKED**. |

A successful scan, address match or GATT read has a distinct meaning. A scan discovers an advertiser. Beat-GN marks a session Ready after characteristic discovery, a successful FEE3 subscription and a valid battery read. A Ready session shows that the selected peripheral responds using the expected profile. Confirm V006213 separately on the watch or via the firmware characteristic.

## GATT profile from protocol evidence

| Service | Characteristic | Use in Beat-GN |
| --- | --- | --- |
| `FEEA` | `FEE1` | Expected live activity notifications. Zero and missing values are distinct. |
| `FEEA` | `FEE2` | Required writable command channel. Uses a response-capable write when available. |
| `FEEA` | `FEE3` | Required framed-notification channel with CCCD `2902`. |
| `FEEA` | `FEE5`, `FEE6`, `FEE7`, `FEE8` | Auxiliary channels in the historical SDK map. Beat-GN has no active feature using them. |
| `180F` | `2A19` | One-byte Battery Level read, 0–100. A valid read earns Ready and powers battery display. |
| `180D` | `2A37` | Standard Heart Rate Measurement notification, with 8/16-bit BPM selected by flags. |
| `180A` | `2A24`, `2A26`, `2A28`, `2A29` | Optional model number, firmware revision, software revision and manufacturer, respectively. [Bluetooth SIG Device Information Service](https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/DIS_v1.2/out/en/index-en.html) |

The repository evidence describes `FE EA`, one version byte, one total-length byte, then opcode and payload. Version `0x10` applies when usable ATT chunk size is 20 bytes and `0x20` otherwise. The assembler handles fragmented and coalesced incoming frames. Current commands fit a single default-MTU write. There are no V006213 captures for verifying the outgoing version choice or the receive layout.

## Commands and capability status

| Feature | Protocol or app evidence | V006213 classification |
| --- | --- | --- |
| Discovery and application connection | Scanning sorts connectable FEEA advertisers first and preserves manual selection. It retains the Android device object and its address type. The selected peer must expose the expected GATT channel and answer a read. | Supported by protocol evidence but not tested on V006213 |
| System BLE bonding | Android may prompt if a protected characteristic requires it. The model manual separately describes calling pairing. | Unknown whether companion GATT requires a bond on V006213 |
| Battery | `180F / 2A19` | Supported by protocol evidence but not tested on V006213 |
| Device ID | `180A / 2A24, 2A26, 2A28, 2A29` | Supported by protocol evidence but not tested on V006213 |
| Time sync | `0x31`, LE32 Unix seconds and signed whole UTC-offset hours. | Supported by protocol evidence but not tested on V006213. Fractional offsets and DST behavior need direct observation. |
| Find watch | `0x61`, empty payload, no reply defined in the SDK dispatch reviewed historically. | Supported by protocol evidence but not tested on V006213 |
| Pulse | `0x6D` with `00`/ `FF` starts/stops a measurement. Its reply is a completion state. BPM arrives from `2A37`. | Supported by protocol evidence but not tested on V006213 |
| SpO₂ | `0x6B` with `00`/ `FF`, percentage reply. | Supported by protocol evidence but not tested on V006213 |
| Blood pressure | `0x69`, start `00 00 00`, stop `FF FF FF`. Historical parser uses a three-byte reply, pressure in bytes 1 and 2. Status meanings on the target are unavailable. | Unknown for this unit. Documented protocol request available as a probe. |
| Steps, distance, calories and active minutes | `FEE1` triplets, little-endian 24-bit fields. All-`FF` means absent. | Supported by protocol evidence but not tested on V006213. Verify units and day-reset semantics on the watch. |
| Create, edit, disable or resend a repeating alarm | `0x11` write with 8-byte slot payload. A successful callback only updates transport status. | Supported by protocol evidence but not tested on V006213 |
| Query alarms | Historical SDK `0xB9` with `15 04`. No validated response decoder or query/write correlation in Beat-GN. | Supported by protocol evidence but not tested on V006213 |
| Watch-side alarm deletion | Phone deletion changes Room. No dedicated remote-delete path in the app. | Unknown on V006213. Use watch controls or verify that a disable write took effect on the watch. |
| Dated alarm | Historical SDK packs year, month and day. Beat-GN’s editor only schedules repeating alarms. | Supported by protocol evidence but not tested on V006213. No app UI for scheduling dated alarms. |
| Reconnect and background link | Android GATT plus a connected-device foreground service. Automatic retry is bounded, and user disconnect is persisted. | Supported by the Android transport model. Behavior on V006213 is blocked. |
| Health history sync | Beat-GN stores readings received while connected. Family history APIs lack validated V006213 operation sequences. | Unknown on V006213 |
| Assistant transcript delivery | The historical SDK’s phone/watch recording controls expose no transcript source to this app. Legacy rows remain visible as legacy entries. | Unsupported by Beat-GN. V006213 device-side assistant capabilities are a separate question. |
| Sleep, stress, camera, music, watch faces, notifications and calling | See [FEATURE_RECOMMENDATIONS.md](FEATURE_RECOMMENDATIONS.md) for product and protocol scope. | Unknown over Beat-GN’s BLE link on V006213 |

**Confirmed working on V006213:** none observed. There is also no device-level negative evidence to classify an otherwise untested measurement as unsupported. The absence of a reply to a BP probe is a timeout.

## Known compatibility risks and fixes

- The manufacturer describes adding the watch in DaFit before pairing for Bluetooth calls. The separate pairing flow makes the calling endpoint a possibility when FEEA is absent. A fresh scan and discovered GATT inventory can establish which endpoint the phone selected. [Manufacturer manual, calling setup](https://www.manualslib.com/manual/4141784/Beatxp-Marv-Neo-Bxsm2001.html?page=5)
- Beat-GN inspects every discovered instance of the known FEEA service and requires its channels within the same instance. It checks the command write properties and packet subscription capabilities before setup. Discovery failures list service and characteristic UUIDs, properties and CCCD presence under More → Diagnostics. A Service Changed callback retires the invalidated GATT session and starts bounded recovery.
- Partial activity notifications keep each reported metric and leave missing fields empty. Room migration preserves existing rows. The heart-rate parser ignores measurements with an explicit contact-loss flag. [Bluetooth SIG Heart Rate Service](https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/HRS_v1.0/out/en/index-en.html)
- Establish the MTU/version, actual write properties, notification subscriptions and battery read behavior on the target. An absent or unreadable `2A19` leaves the app Unstable. A dropped non-MTU operation closes the ambiguous GATT session before retry.
- Time sync preserves the existing protocol shape. The encoded offset is `5` in India Standard Time because the field has whole-hour granularity. Verify the watch shows the correct minute before changing any encoding.
- BLE callbacks are matched to link, operation and characteristic. Health replies can complete a request before its write callback. The app now labels alarms and find-watch outcomes as transport results.
- Full MACs and raw payloads are omitted from app diagnostics. Device info shows an address suffix, and scanning stays available if the platform filters advertisements or the address changes.
- Prioritize a read-only profile capture, alarm read-back using observed replies, and historical sync only after a matching V006213 capture. See the [device checklist](DEVICE_TESTING_CHECKLIST.md) and [test results](TEST_RESULTS.md).
