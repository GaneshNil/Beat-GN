# Test results

Executed on 17 November 2026 in the existing Beat-GN checkout. Android SDK 35, Android build-tools
34/35, Android platform-tools ADB 37.0.1, Temurin JDK 17.0.21 and Gradle 8.7 were installed under the
local toolchain and Gradle cache. The first shell attempt with the default environment failed because
the sandbox made the normal Gradle cache read-only. The rerun used the installed JDK/SDK and approved
cache access.

## Commands and outcomes

| Run | Command | Result |
| --- | --- | --- |
| Baseline, AGP 8.4.1 | `./gradlew clean testDebugUnitTest lintDebug assembleDebug --console=plain` | **PASS**. 75 tests, 0 failures, 0 errors, 0 skipped, debug APK produced. Lint XML had 35 warnings and 2 informational findings. Plugin warned that its tested SDK limit was 34. |
| Focused reproduction | `./gradlew --offline testDebugUnitTest --tests '*CrrepaProtocolTest' --tests '*HealthRepositoryTest' --console=plain` | **Expected failures**. 40 tests, 2 failures. Unknown frame version was accepted by the direct parser and two concurrent copies of one oxygen reading were inserted. |
| Intermediate compilation | `./gradlew testDebugUnitTest --console=plain` | First integrated attempt caught a stale variable reference in the blood-pressure Compose card. The source reference was corrected. Subsequent unit and clean build runs passed. |
| Integrated clean run, AGP 8.6.1 | `./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain` | **PASS**. 90 unit tests and both debug APKs built before the final calendar, backup and UI fixes. |
| Final clean run, AGP 8.6.1 | `./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease --console=plain --offline` | **PASS** in 1m 5s. 127 tasks executed. 93 JVM tests passed. Debug, instrumentation and unsigned release APKs built. Release vital lint and R8 succeeded. |
| Dependency graph | `./gradlew --offline :app:dependencies --configuration releaseRuntimeClasspath --console=plain` | **PASS**. Release deps resolved. SQLite JDBC, JUnit and AndroidX test runner were excluded from the release runtime graph. |
| Known-vulnerability lookup | OSV `POST /v1/querybatch` with the 87 resolved release runtime Maven coordinates | **0 advisory matches**. Release runtime Maven scope. The first sandboxed attempt could not resolve the API hostname. The escalated read-only lookup succeeded. |
| ADB | `adb devices -l` | **BLOCKED** for device tests. The unrestricted ADB list was empty on the initial check and the final check. No configured SDK emulator, AVD or KVM device was present. |

The final Gradle command uses the installed toolchain:

```bash
export JAVA_HOME=/home/ganesh/android-toolchain/jdk
export ANDROID_HOME=/home/ganesh/android-toolchain/sdk
export ANDROID_SDK_ROOT=/home/ganesh/android-toolchain/sdk
./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease --console=plain --offline
```

## Final JVM results

| Suite | Passed | Coverage |
| --- | ---: | --- |
| GattOperationsTest | 6 | Ownership, wrong operation/attribute, late callbacks, invalidation, timeout, cancellation and retry budget |
| MeasurementRequestTest | 3 | Early completion, repeated tap and link loss during a write |
| CrrepaProtocolTest | 34 | Framing, MTU selection, fragment reassembly, invalid lengths/versions, commands, and measurement fields |
| MigrationSqlTest | 6 | v1–v4 SQLite schema, existing health row, transcript evolution and alarm defaults |
| HealthRepositoryTest | 11 | Validation, race-safe dedupe, day rollover, step snapshots, alarm success/failure and deletion |
| AlarmSlotsTest | 4 | First free, gaps, full slots and malformed slot collections |
| HealthAggregatorTest | 25 | Calendar windows, DST, timezone, empty days, sample validity and dedupe |
| SparkLabelIndicesTest | 4 | Chart label index selection |
| **Total** | **93** | **0 failed, 0 errors, 0 skipped** |

The Room instrumentation APK contains one Android test, `RoomMigrationTest`. It compiles. Execution
of that test, rendering/UI interactions and all physical BLE cases are **BLOCKED**. The Android test
did not contribute to the 93 JVM results. Protocol fixtures represent byte layouts from the
historical evidence or synthetic boundary cases. No V006213 device capture was used.

## Final lint and artifacts

The final offline lint XML reported **0 errors, 5 warnings and 2 informational findings**. Warnings
are four Compose modifier-order findings and a version-qualified launcher resource whose qualifier
is lower than the minimum SDK. Informational entries cover two boxed integer Compose states.
Offline lint skips online dependency currency checks. The earlier online integrated build also
emitted dependency upgrade notices. No warning baselines or suppressions were introduced.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | 53,473,607 | `2af32f73b52427f62b1417b1ead63f63c66b575ce2467a2f6a94eb2f161febdc` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 717,805 | `4e17f85539e2f3c1c8268948eaa0d555d7ec723e3e72432bc6337dc610edae5d` |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 2,070,723 | `a633e82d3e2e4ebce10910f872c2551fb22abf798e92d43ebd02169637c402f5` |

Results were read from Gradle’s test and lint XML. The merged app manifest and source were checked
for unnecessary permissions and diagnostic disclosures. Git diff whitespace and Room schema
checks passed. Line or branch coverage percentages are unavailable because the project has no
coverage task. Device-level BLE compatibility, actual permissions/service behavior, Compose
interaction, TalkBack and SQLite framework migration still require [physical tests](DEVICE_TESTING_CHECKLIST.md).

## 22 November 2026 follow-up for 1.1.3

The previous startup patch passed the Android 16 emulator job after the lifecycle-owner fix was
merged. The owner's Android 16 screenshot showed Home rendering and a selected device reporting no
FEEA service. [Baseline GitHub Actions run](https://github.com/GaneshNil/Beat-GN/actions/runs/44052039407)

Run with the installed JDK 17 and SDK 35:

```bash
./gradlew --offline clean testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease --console=plain
```

- **Build** succeeded in 2 minutes 37 seconds. Debug, Android test and unsigned release APKs were produced. Release R8 and resource shrinking completed.
- **JVM tests** passed 104 of 104 with zero failures, errors or skipped tests. Eleven new tests cover scan result merging and ranking, address redaction, late measurement write callbacks, heart-rate sensor contact, partial activity storage, SQLite migration, alarm write failure and transcript deletion.
- **Lint** reported zero errors, five existing warnings and two existing informational results. Dependency checks were offline.
- **Android test APK** compiled seven tests. One checks fresh launch, Watch permission recovery UI and About. Two exercise Room and SQLite on Android. Four use synthetic Android GATT service inventories to verify the profile resolver. The GitHub workflow runs these on API 36 and gates APK upload on their result. [Build workflow](https://github.com/GaneshNil/Beat-GN/actions/workflows/build-apk.yml)
- **Physical watch tests** remain blocked. ADB listed no phone. A 25-second host BLE scan found no matching target. See the [firmware report](MARV_NEO_V006213_COMPATIBILITY.md) and [manual checklist](DEVICE_TESTING_CHECKLIST.md).
