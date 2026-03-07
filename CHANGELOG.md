# Changelog

## 0.1.3 (2026-03-07)

### Fixed

- Refactored `openEsimSetup` Android implementation to use the `Promise` callback pattern instead of `suspendCoroutine` — `AsyncFunction` lambdas in expo-modules-core 55.x do not run in a suspend context, causing a Kotlin compilation error

## 0.1.2 (2026-03-07)

### Fixed

- Removed explicit `kotlin-stdlib-jdk7` dependency from `android/build.gradle` — `getKotlinVersion()` was removed in newer Android Gradle Plugin versions and the stdlib is included automatically by the Kotlin Gradle plugin (Kotlin 1.8+)

## 0.1.1 (2026-02-18)

### Fixed

- Corrected TypeScript typing for `openEsimSetup` bridge in `ExpoEsimUtilsModule` from `Promise<boolean>` to `Promise<EsimSetupResult>`
- Aligns native module interface with exported `openEsimSetup()` contract and eliminates downstream app typecheck failures

## 0.1.0 (2026-02-17)

Initial release.

### Features

- **`isEsimSupported()`** — Synchronous eSIM hardware detection
  - iOS: `CTCellularPlanProvisioning.supportsCellularPlan()` (iOS 12+)
  - Android: `EuiccManager.isEnabled()` (Android 9+)

- **`getEsimCapability()`** — Detailed eSIM capability info including platform, support reason, eUICC firmware version (Android), SIM port availability (Android 13+), and active cellular plans

- **`getActivePlans()`** — List active cellular plans with carrier name, MCC/MNC, country code, VoIP capability (iOS), embedded status (Android)

- **`openEsimSetup(activationCode?)`** — Install eSIM profiles
  - iOS 17.4+: Opens Apple's Universal Link (`esimsetup.apple.com`) for native eSIM installation — no carrier entitlement required
  - iOS < 17.4: Returns `"unsupported"` for QR code/manual fallback
  - Android 9+: Uses `EuiccManager.downloadSubscription()` with system consent dialog, falls back to eSIM settings with clipboard

### Platform Support

- iOS 12.0+ (detection), iOS 17.4+ (installation)
- Android API 28+ (Android 9+)
- Expo SDK 50+
- Requires development build (not compatible with Expo Go)
