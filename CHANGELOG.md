# Changelog

## 0.4.0 (2026-11-15)

### Added

- `installEsim(activationCode)`: installs a profile and resolves with what actually happened. On Android it keeps listening after the system consent dialog, so the result is `success` or `failed` rather than `settings_opened`, and a failure carries Android's `resultCode`, `detailedCode`, `operationCode`, `errorCode` and the SM-DP+ subject and reason codes. Resolves `timeout` if the system reports nothing within four minutes. On iOS it opens Apple's install flow and resolves `settings_opened`, as before.

### Fixed

- Android: `openEsimSetup()` stopped listening after the first callback. When the system asked the user to confirm the download, the final result was delivered to a receiver that had already been unregistered, so the app could never learn whether the install succeeded. `openEsimSetup()` keeps its old return values for compatibility; use `installEsim()` to get the outcome.
- Android: the activation code was passed to `DownloadableSubscription.forActivationCode()` with its `LPA:` scheme. `EuiccManager` expects the bare SGP.22 activation code (`1$<smdp>$<matching id>`), and phones differ on whether they tolerate the scheme. The scheme is now removed for the direct download and always present on the `esimsetup.android.com` link, whichever form the caller passes.

## 0.3.1 (2026-10-22)

### Fixed

- Import `requireNativeModule` from `expo` instead of `expo-modules-core`. Expo SDK 58 no longer hoists `expo-modules-core` for dependents, so the old import failed to resolve in apps that do not depend on it directly.

## 0.3.0 (2026-07-13)

### Added

- `getEsimCapability()` now returns a `confidence` field: `'confirmed'` (OS-verified — always on Android, and on iOS when the carrier entitlement is present), `'assumed'` (iOS model heuristic — not OS-verified), or `'unknown'` (iOS iPad only — an unrecognized model identifier, most likely a new device released after this library's last update; `isSupported` defaults to `false` but the device may actually support eSIM). Requested in [#1](https://github.com/KwaminaWhyte/expo-esim-utils/issues/1) as a way to distinguish a real detection from a heuristic guess.
- iOS: the device-model fallback now reports `'unknown'` instead of silently guessing `false` for iPad identifiers it can't classify (major version `>= 7`, i.e. the eSIM-capable era, but not in the known Cellular allowlist) — these could be an un-catalogued new Cellular iPad or a Wi-Fi-only unit, and there's no way to tell without a library update.

### Changed

- `isEsimSupported()` behavior is unchanged (still a plain boolean, `unknown` conservatively maps to `false`) — this is a non-breaking, additive release.

## 0.2.2 (2026-07-13)

### Fixed

- `isEsimSupported()` / `getEsimCapability()` returning `false` on eSIM-capable iPad Cellular models (Air, mini, Pro, and base iPad). The 0.2.1 model-fallback only recognized `iPhone*` identifiers. iPad Wi-Fi and Wi-Fi+Cellular are different hardware SKUs with different model identifiers (unlike iPhone, where every unit ships with both nano-SIM and eSIM), so the fallback now checks the Cellular identifier against an explicit allowlist built from Apple's official eSIM-capable device list instead of a version cutoff. ([#1](https://github.com/KwaminaWhyte/expo-esim-utils/issues/1))
- Confirmed `CTCellularPlanProvisioning.supportsEmbeddedSIM` is gated behind the same carrier-only entitlement as `supportsCellularPlan()` and returns `false` for non-carrier apps on real hardware — so it isn't a viable alternative detection path either, on iPhone or iPad.

## 0.2.1 (2026-05-11)

### Fixed

- `isEsimSupported()` returning `false` on iOS for eSIM-capable iPhones (XS, SE 2020, 14, 15, 16 Pro, …). `CTCellularPlanProvisioning.supportsCellularPlan()` requires Apple's carrier-only commercial eSIM entitlement and returns `false` for any app without it. Added a hardware-model fallback that treats `iPhone11,*` (XS/XR) and later as eSIM-capable when the CoreTelephony API reports `false`. ([#1](https://github.com/KwaminaWhyte/expo-esim-utils/issues/1))
- `getEsimCapability()` now includes the iOS `deviceModel` identifier and a clearer `reason` string when capability is detected via the model fallback.

## 0.2.0 (2026-04-22)

### Added

- Android Universal Link fallback for `openEsimSetup` — opens `https://esimsetup.android.com/esim_qrcode_provisioning?carddata=<code>` when `EuiccManager.downloadSubscription()` is unavailable or fails (Android 10+)
- New `EsimSetupResult` value: `"universal_link_opened"`

### Changed

- Android `openEsimSetup` fallback order: `downloadSubscription` → Universal Link → clipboard + eSIM settings

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
