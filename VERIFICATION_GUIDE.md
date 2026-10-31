# beatXP Marv Neo Companion App — Verification & Testing Guide

This guide details how to build, install, pair, and test the companion application on your smartphone, with specific configuration steps for **OriginOS 6+ (vivo / iQOO)** and **Android 11–16**.

---

## 1. Prerequisites

* **Development Environment**: Android Studio (Hedgehog or newer) with **JDK 17** (`java -version`).
* **Target Smartphone**: Any Android device running **Android 11 (API 30) up to Android 16 (API 35/36)**.
* **Physical Watch**: beatXP Marv Neo (charged, Bluetooth active).

---

## 2. Locating the Watch MAC Address

1. On the watch, swipe down on the home screen to open the Quick Settings panel.
2. Tap the **Settings (Gear icon)**.
3. Scroll down and tap **About**.
4. Note the **MAC Address** (format: `XX:XX:XX:XX:XX:XX`).

---

## 3. Building & Installing the APK

Open your terminal in the project directory:

```bash
cd /home/ganesh/Documents/Random_things/MarvNeoCompanion

# Compile the optimized debug build
./gradlew assembleDebug

# Install via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 4. First-Time Setup & OriginOS 6+ Optimization

### General Android Setup (Android 11 - 16)
1. Open **Marv Neo Companion**.
2. **Grant Permissions**:
   * **Nearby Devices / Bluetooth**: Tap **Allow**.
   * **Notifications**: Tap **Allow** (Required for foreground service persistent state).
3. **Enable Notification Access**:
   * In phone settings: **Settings $\rightarrow$ Apps $\rightarrow$ Special App Access $\rightarrow$ Notification Access**.
   * Enable **Marv Neo Companion**.

### OriginOS 6+ (vivo / iQOO) Keep-Alive Configuration
OriginOS enforces aggressive background memory management that kills background Bluetooth connections unless whitelisted:
1. Open the **Settings** tab in **Marv Neo Companion**.
2. Tap **Disable Battery Saver** $\rightarrow$ select **Allow** on the system battery optimization dialog.
3. Tap **Vivo Auto-Start** (or manually navigate to **i Manager $\rightarrow$ App Manager $\rightarrow$ Permission Management $\rightarrow$ Auto-start**):
   * Toggle **Marv Neo Companion** to **Allowed**.
4. In **Settings $\rightarrow$ Battery $\rightarrow$ Background Power Consumption Management**:
   * Set **Marv Neo Companion** to **Allow High Background Power Consumption**.

---

## 5. Pairing the Watch

1. On the **Dashboard** tab of the app:
2. Enter your watch's MAC address in the input field.
3. Tap **Connect Watch**.
4. The status indicator will switch to **`Connected • BLE Active`**.
5. Once connected:
   * The watch clock will synchronize with the phone's time.
   * Battery level will appear on the status badge.
   * A persistent, silent service notification will appear in your status bar.

---

## 6. Functional Verification Checklist

| Test Item | Action | Expected Physical Result |
| :--- | :--- | :--- |
| **Edge-to-Edge Fluidity** | Scroll the Dashboard on a 120Hz/144Hz screen. | Butter-smooth 120 FPS scrolling with dynamic translucent system bars and tactile haptics. |
| **Time Sync** | Tap **Sync Time** button on Dashboard. | Watch displays identical time and date as phone. |
| **Live Steps** | Walk 20 steps, then tap **Refresh**. | Step count and estimated kcal update; animated progress bar smoothly increments. |
| **Heart Rate** | Tap **Measure Now** under Heart Rate card. | Green optical PPG sensor illuminates underneath the watch; live BPM streams to phone. |
| **SpO2 (Blood Oxygen)** | Tap **Check SpO2** under Blood Oxygen card. | Red optical sensor turns on; oxygen saturation percentage updates after ~15 seconds. |
| **Find Watch** | Tap **Ring Watch** on Settings tab. | Watch vibrates continuously and screen turns on until **Stop** is tapped. |
| **Raise to Wake** | Toggle **Raise to Wake** switch ON/OFF. | Rotating your wrist activates the watch screen according to setting. |
| **WhatsApp / SMS Alerts** | Send a test WhatsApp message to the phone. | Watch immediately buzzes and displays message sender name and body preview. |
| **Custom Watch Face** | Go to **Studio** tab $\rightarrow$ adjust clock position $\rightarrow$ tap **Flash Custom Watch Face**. | Progress bar increments; watch displays download screen and switches to the new custom dial upon completion. |

---

## 7. Enabling Bluetooth Calling (Microphone & Speaker)

The Marv Neo uses dual-mode Bluetooth (BLE for health/data, Bluetooth Classic for audio calling):
1. On the watch, swipe down and ensure the **Phone / Call Audio icon** is toggled ON (blue/active).
2. On your phone, open system **Bluetooth Settings**.
3. Scan for available devices and tap **Marv Neo Call** (or `Watch Call`).
4. Accept the pairing prompt. Phone calls will now route directly through the watch's microphone and speaker.
