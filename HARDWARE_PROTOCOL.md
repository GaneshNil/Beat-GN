# beatXP Marv Neo (Crrepa/DaFit) BLE Hardware Protocol Specification

## 1. Overview
The **beatXP Marv Neo** smartwatch runs on the **Crrepa / MoYoung / DaFit** hardware platform. Communication is conducted over Bluetooth Low Energy (BLE) using custom GATT characteristics, supplemented with Bluetooth Classic (BR/EDR) for audio calls and microphone/speaker hands-free features.

---

## 2. GATT Services & Characteristics

| Entity | UUID | Properties | Purpose |
| :--- | :--- | :--- | :--- |
| **Primary Service (NUS)** | `6e400001-b5a3-f393-e0a9-e50e24dcca9e` | - | Nordic UART Service container |
| **TX (Write to Watch)** | `6e400002-b5a3-f393-e0a9-e50e24dcca9e` | Write, Write Without Response | Commands, settings, notifications, time sync |
| **RX (Notify from Watch)** | `6e400003-b5a3-f393-e0a9-e50e24dcca9e` | Notify, Indicate | Telemetry (Steps, HR, SpO2, Sleep, button triggers) |
| **DFU / OTA Service** | `0000ffd0-0000-1000-8000-00805f9b34fb` | - | Firmware & Watch face flashing service |
| **DFU Control Point** | `0000ffd1-0000-1000-8000-00805f9b34fb` | Write, Notify | Firmware update handshakes & block verification |
| **DFU Packet Stream** | `0000ffd2-0000-1000-8000-00805f9b34fb` | Write Without Response | Stream of raw binary chunks (Watch Face / Bin) |

---

## 3. Packet Structure & Framing

All standard control packets sent to `6e400002` are byte arrays with a minimum length of 4 bytes and maximum MTU length (typically negotiated to 512, default 20 bytes).

### Standard Packet Framing
```
+-----------+-----------+-------------+---------------------+----------+
| Header    | Length    | Command ID  | Payload Bytes       | Checksum |
| (1 byte)  | (1 byte)  | (1 byte)    | (0 .. N bytes)      | (1 byte) |
+-----------+-----------+-------------+---------------------+----------+
```
* **Command ID (Opcode)** dictates the subsystem (Time, Sensor, Control, Notification).
* **Checksum**: XOR sum or simple byte sum of `(Length + Command + Payload) & 0xFF`.

---

## 4. Initialization & Handshake Flow

Immediately upon establishing GATT connection:
1. **Request MTU**: Call `gatt.requestMtu(512)` to maximize transfer throughput.
2. **Enable Notifications**: Write CCCD descriptor (`00002902-0000-1000-8000-00805f9b34fb`) on `6e400003` to `ENABLE_NOTIFICATION_VALUE` (`0x01, 0x00`).
3. **Time Sync Packet**:
   * Command: `0x01`
   * Format: `[0x01, Year(2B), Month(1B), Day(1B), Hour(1B), Min(1B), Sec(1B)]`
4. **Query Battery Level**:
   * Command: `0x03`
   * Watch Response: `[0x03, BatteryPercent(0-100), ChargingState(0/1)]`
5. **Query Device Capabilities & Firmware**:
   * Command: `0x1C`
   * Watch Response: Firmware version string and feature mask (HR, SpO2, BP, Stress).

---

## 5. Health Telemetry & Sensor Parsing

Telemetry packets arrive via `6e400003` notification events:

### A. Live Steps & Daily Activity (Command `0x02` or `0x11`)
* **Request**: `[0x11, 0x01, 0x00]`
* **Response Frame**:
  * Bytes 0..3: Step count (`uint32_t`, Little-Endian)
  * Bytes 4..7: Calorie expenditure in kcal (`uint32_t`, Little-Endian)
  * Bytes 8..11: Distance traversed in meters (`uint32_t`, Little-Endian)
  * Bytes 12..15: Active exercise duration in seconds (`uint32_t`)

### B. Heart Rate (Command `0x12`)
* **Real-time Trigger**: `[0x12, 0x01]` (Start), `[0x12, 0x00]` (Stop)
* **Response Packet**:
  * Byte 0: `0x12`
  * Byte 1: Current Heart Rate in BPM (`uint8_t`, range: 40–220)
  * Byte 2: Dynamic status code (0: Resting, 1: Active/Sports, 2: Measurement error)

### C. Blood Oxygen / SpO2 (Command `0x15`)
* **Trigger**: `[0x15, 0x01]`
* **Response Packet**:
  * Byte 0: `0x15`
  * Byte 1: SpO2 percentage (`uint8_t`, e.g., `98` for 98%)
  * Byte 2: Measurement status (0: Completed, 1: Measuring, 255: Failed/Finger moved)

### D. Sleep Tracking (Command `0x18`)
* **Request Sleep History**: `[0x18, 0x00]` (Current day), `[0x18, 0x01]` (Previous day)
* **Response Payload Format**:
  * Total Sleep Minutes (`uint16_t`)
  * Restful / Deep Sleep Minutes (`uint16_t`)
  * Light Sleep Minutes (`uint16_t`)
  * REM Sleep Minutes (`uint16_t`)
  * Sober / Awake Minutes (`uint16_t`)
  * Followed by array of transition timestamps and state IDs:
    * `0x01`: Deep Sleep
    * `0x02`: Light Sleep
    * `0x03`: REM Sleep
    * `0x04`: Awake

---

## 6. Device Control Byte Codes

Packets sent to `6e400002` to trigger device actions:

| Action | Byte Array | Description |
| :--- | :--- | :--- |
| **Find Watch** | `[0x07, 0x01, 0x01]` | Causes watch to vibrate and illuminate screen |
| **Cancel Find Watch** | `[0x07, 0x01, 0x00]` | Stops vibration on watch |
| **Find Phone (From Watch)** | `0x08, 0x01` (Received) | Watch triggers phone alarm/sound |
| **Camera Shutter Open** | `[0x09, 0x01]` | Enters camera shutter UI mode on watch |
| **Camera Shutter Click** | `0x09, 0x02` (Received) | User tapped watch screen to take photo |
| **Camera Shutter Close** | `[0x09, 0x00]` | Exits camera mode on watch |
| **Raise to Wake ON** | `[0x05, 0x01, StartHr, StartMin, EndHr, EndMin]` | Enables wrist rotation screen wake |
| **Raise to Wake OFF** | `[0x05, 0x00]` | Disables wrist rotation screen wake |
| **Vibration Strength** | `[0x20, Level]` | `0x00`: Off, `0x01`: Soft, `0x02`: Strong |
| **12/24 Hour Format** | `[0x0A, 0x01]` (12h) / `[0x0A, 0x00]` (24h) | Time system format |

---

## 7. Notification Push Protocol

When the phone receives a notification, `WatchNotificationListenerService` frames and sends a message packet to the watch.

### Notification Command: `0x23` or `0x24`
```
[0x23, AppTypeByte, TitleLength, TitleBytes..., MessageLength, MessageBytes...]
```

### App Type Mapping (`CRPBleMessageType`):
* `0x01`: Incoming Phone Call (`MESSAGE_PHONE`)
* `0x02`: SMS Text Message (`MESSAGE_SMS`)
* `0x03`: WeChat (`MESSAGE_WECHAT`)
* `0x04`: Facebook (`MESSAGE_FACEBOOK`)
* `0x05`: Twitter / X (`MESSAGE_TWITTER`)
* `0x06`: WhatsApp (`MESSAGE_WHATSAPP`)
* `0x07`: Instagram (`MESSAGE_INSTAGREM`)
* `0x08`: Skype (`MESSAGE_SKYPE`)
* `0x09`: Telegram (`MESSAGE_TELEGRAM`)
* `0x0A`: LinkedIn (`MESSAGE_LINKEDIN`)
* `0x0B`: Gmail (`MESSAGE_GMAIL`)
* `0x0C`: YouTube (`MESSAGE_YOUTUBE`)
* `0xFF`: Generic / Other (`MESSAGE_OTHER`)

### Constraints:
* Maximum title length: 32 bytes (UTF-8).
* Maximum message length: 128 bytes (UTF-8). Excess text is cleanly truncated.

---

## 8. Custom Watch Face Pipeline

The Marv Neo uses a rectangular display (typically 240×280 or 320×360 px):
1. **Color Format**: **RGB565** (16-bit Big Endian: 5 bits Red, 6 bits Green, 5 bits Blue).
2. **Binary Header**:
   ```
   [Width(2B), Height(2B), TimePositionX(2B), TimePositionY(2B), TextColor(2B), CompressionType(1B)]
   ```
3. **Flashing Protocol**:
   * Send dial start packet on `0000ffd1` with image byte size and CRC32.
   * Send image payload in 256-byte chunks to `0000ffd2` with incremental packet sequence numbering.
   * Await transmission completion signal from watch before restoring normal NUS mode.

