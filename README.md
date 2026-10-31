# Marv Neo Companion App

A modern, privacy-focused, zero-telemetry companion application for the **beatXP Marv Neo** smartwatch (and compatible Crrepa/DaFit hardware devices), engineered with **Kotlin**, **Jetpack Compose**, and **Material 3**.

---

## Key Highlights

* **Android 11 through Android 16 Ready**:
  * `minSdk = 30` (Android 11) for broad device compatibility.
  * `targetSdk = 35` (Android 15 / 16 preview) with mandatory **Edge-to-Edge** layout enforcement (`enableEdgeToEdge`) and dynamic window insets.
  * **Material You Dynamic Colors**: Automatically adapts to user wallpaper palette on Android 12+.
* **OriginOS 6+ (vivo / iQOO) Optimization**:
  * Specialized background keep-alive protocol with `PARTIAL_WAKE_LOCK` burst synchronization.
  * One-tap direct shortcuts to OriginOS **High Background Power Consumption** and **Auto-start Manager** (`com.vivo.permissionmanager`, `com.iqoo.secure`).
  * `FOREGROUND_SERVICE_IMMEDIATE` flag preventing background process termination by aggressive OEM task killers.
* **120Hz / 144Hz LTPO Ultra-Smooth Fluidity**:
  * Hardware acceleration enabled across all rendering pipelines.
  * Spring physics animations (`spring(stiffness = 200f)`).
  * Tactile haptic feedback on all interactions (`LocalHapticFeedback`).
* **Direct Hardware Communication**:
  * Communicates directly over the Nordic UART Service (NUS) GATT pipeline.
  * Thread-safe sequential command queue (`Mutex`) preventing GATT Status 133 collisions.
* **100% Offline & Private**:
  * Zero third-party telemetry SDKs (Mixpanel, UserExperior, Firebase stripped out).
  * Offline-first local health history powered by **Android Room**.

---

## Documentation

* [Hardware Protocol Specification](HARDWARE_PROTOCOL.md): Complete byte-level breakdown of GATT services, packet formats, opcodes, and telemetry parsers.
* [Verification & Testing Guide](VERIFICATION_GUIDE.md): Step-by-step instructions for building, pairing, and verifying all watch capabilities.
