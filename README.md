# expo-esim-utils

Native eSIM detection and installation for Expo and React Native. Detect eSIM support, read active cellular plans, and install eSIM profiles — all from JavaScript.

**iOS** uses Apple's Universal Link (iOS 17.4+) for one-tap eSIM installation and CoreTelephony for detection. **Android** uses `EuiccManager.downloadSubscription()` for direct profile download with a system consent dialog.

No carrier entitlement required. Works for any eSIM reseller or MVNO app.

## Features

| Feature | iOS | Android |
|---|---|---|
| Detect eSIM support | iOS 12+ | Android 9+ |
| Get detailed capability info | iOS 12+ | Android 9+ |
| List active cellular plans | iOS 12+ | Android 5.1+ |
| Install eSIM profile | iOS 17.4+ | Android 9+ |

### How eSIM Installation Works

Building an eSIM app? Here's what you need to know:

- **iOS 17.4+**: Apple provides a [Universal Link](https://support.apple.com/en-us/118669) (`esimsetup.apple.com`) that opens the native eSIM installation screen. No special entitlement needed — this is how Airalo, Holafly, and other major eSIM apps work.
- **iOS < 17.4**: No entitlement-free install API exists. The module returns `"unsupported"` so you can show a QR code or manual activation code as fallback.
- **Android 9+**: Uses `EuiccManager.downloadSubscription()` which shows a system consent dialog. The user confirms and the profile downloads directly. Falls back to opening the eSIM settings screen with the activation code on the clipboard.

> **Note**: `CTCellularPlanProvisioning.addPlan()` requires Apple's carrier entitlement (`com.apple.CommCenter.fine-grained`) which is only granted to MNOs (mobile network operators). If you're a reseller or MVNO, that API won't work for you. This module uses the Universal Link approach instead.

## Installation

```bash
npx expo install expo-esim-utils
```

Or with npm/yarn:

```bash
npm install expo-esim-utils
# or
yarn add expo-esim-utils
```

### Requirements

- Expo SDK 50+
- iOS 12.0+ (detection), iOS 17.4+ (installation)
- Android API 28+ (Android 9+)
- **Requires a [development build](https://docs.expo.dev/develop/development-builds/introduction/)** — will not work in Expo Go

After installing, rebuild your app:

```bash
# iOS
npx expo run:ios

# Android
npx expo run:android
```

## API

### `isEsimSupported()`

Check if the device supports eSIM. Synchronous — returns instantly.

```ts
import { isEsimSupported } from 'expo-esim-utils';

if (isEsimSupported()) {
  console.log('This device supports eSIM!');
}
```

**Returns**: `boolean`

| Platform | Method |
|---|---|
| iOS 12+ | `CTCellularPlanProvisioning.supportsCellularPlan()` |
| Android 9+ | `EuiccManager.isEnabled()` |
| Web | Always returns `false` |

---

### `getEsimCapability()`

Get detailed eSIM capability information including platform, support status, and active plans.

```ts
import { getEsimCapability } from 'expo-esim-utils';

const capability = getEsimCapability();
console.log(capability.isSupported); // true
console.log(capability.reason);      // "Device supports eSIM via CoreTelephony"
console.log(capability.activePlans); // [{ slot: "0", carrierName: "T-Mobile", ... }]
```

**Returns**: [`EsimCapability`](#esimcapability)

---

### `getActivePlans()`

Get a list of active cellular plans on the device.

```ts
import { getActivePlans } from 'expo-esim-utils';

const plans = getActivePlans();
for (const plan of plans) {
  console.log(`${plan.carrierName} (slot ${plan.slot})`);
  // "T-Mobile (slot 0)"
  // "Airalo (slot 1)" — isEmbedded: true (Android)
}
```

**Returns**: [`CellularPlan[]`](#cellularplan)

> **Android**: Requires `READ_PHONE_STATE` permission for full details on Android 10+. Returns an empty array without it (does not crash).

---

### `openEsimSetup(activationCode?)`

Install an eSIM profile on the device. This is the main function for eSIM installation.

```ts
import { openEsimSetup } from 'expo-esim-utils';

const result = await openEsimSetup('LPA:1$smdp.example.com$ACTIVATION_CODE');

switch (result) {
  case 'settings_opened':
    // iOS: user is on the native eSIM install screen
    // Android: consent dialog shown or settings opened
    break;
  case 'success':
    // Android: profile downloaded successfully
    break;
  case 'unsupported':
    // iOS < 17.4: show QR code or manual fallback
    break;
  case 'fail':
    // Something went wrong
    break;
}
```

**Parameters**:

| Name | Type | Description |
|---|---|---|
| `activationCode` | `string` (optional) | eSIM activation code in LPA format: `LPA:1$<SMDP_ADDRESS>$<MATCHING_ID>` |

**Returns**: `Promise<`[`EsimSetupResult`](#esimsetupresult)`>`

| Result | Meaning |
|---|---|
| `"settings_opened"` | Native eSIM install screen opened (iOS 17.4+ / Android) |
| `"success"` | Profile downloaded successfully (Android only) |
| `"unsupported"` | Direct install not available — show QR/manual fallback |
| `"fail"` | Installation failed |
| `"unknown"` | Result could not be determined |

---

## Types

### `EsimCapability`

```ts
type EsimCapability = {
  isSupported: boolean;
  platform: 'ios' | 'android';
  reason: string;
  osVersion?: string;            // Android: eUICC firmware version
  isSimPortAvailable?: boolean;  // Android 13+: whether a SIM port is free
  activePlans?: CellularPlan[];
};
```

### `CellularPlan`

```ts
type CellularPlan = {
  slot: string;
  carrierName?: string;
  mobileCountryCode?: string;   // MCC
  mobileNetworkCode?: string;   // MNC
  isoCountryCode?: string;
  allowsVOIP?: boolean;         // iOS only
  subscriptionId?: number;      // Android only
  isEmbedded?: boolean;         // Android only: true if eSIM
};
```

### `EsimSetupResult`

```ts
type EsimSetupResult = 'success' | 'fail' | 'unknown' | 'settings_opened' | 'unsupported';
```

## Full Example

A typical eSIM installation screen with install button, QR code, and manual fallback:

```tsx
import { useState } from 'react';
import { Alert, Button, Platform, View } from 'react-native';
import QRCode from 'react-native-qrcode-svg';
import { isEsimSupported, openEsimSetup } from 'expo-esim-utils';

export function InstallEsim({ activationCode }: { activationCode: string }) {
  const [installing, setInstalling] = useState(false);

  const handleInstall = async () => {
    setInstalling(true);
    try {
      const result = await openEsimSetup(activationCode);

      if (result === 'settings_opened') {
        // User is on the native install screen — they'll return when done
      } else if (result === 'unsupported') {
        Alert.alert('Use QR Code', 'Direct install requires iOS 17.4+. Scan the QR code below.');
      } else if (result === 'success') {
        Alert.alert('Installed!', 'Your eSIM has been installed successfully.');
      } else {
        Alert.alert('Failed', 'Try using the QR code or manual method.');
      }
    } catch {
      Alert.alert('Error', 'eSIM installation is not available.');
    } finally {
      setInstalling(false);
    }
  };

  return (
    <View>
      <Button
        title={installing ? 'Installing...' : 'Install to Device'}
        onPress={handleInstall}
        disabled={installing}
      />

      {/* QR code fallback */}
      <QRCode value={activationCode} size={200} />
    </View>
  );
}
```

## Comparison with Other Libraries

| Feature | expo-esim-utils | react-native-esim | react-native-sim-cards-manager |
|---|---|---|---|
| Expo Module (no linking) | Yes | No | No |
| iOS install (no entitlement) | Yes (Universal Link) | No (requires carrier entitlement) | No (requires carrier entitlement) |
| Android install | Yes (downloadSubscription) | No | Yes |
| eSIM detection | Yes | Yes (iOS only) | Yes |
| Active plan listing | Yes | No | Yes |
| Maintained | Yes | No (2020) | Yes |

## How It Works Under the Hood

### iOS

| Function | Native API |
|---|---|
| `isEsimSupported()` | `CTCellularPlanProvisioning.supportsCellularPlan()` |
| `getEsimCapability()` | `CTCellularPlanProvisioning` + `CTTelephonyNetworkInfo` |
| `getActivePlans()` | `CTTelephonyNetworkInfo.serviceSubscriberCellularProviders` |
| `openEsimSetup()` | `UIApplication.open("https://esimsetup.apple.com/...")` (iOS 17.4+) |

### Android

| Function | Native API |
|---|---|
| `isEsimSupported()` | `EuiccManager.isEnabled()` |
| `getEsimCapability()` | `EuiccManager` + `EuiccInfo` |
| `getActivePlans()` | `SubscriptionManager.getActiveSubscriptionInfoList()` |
| `openEsimSetup()` | `EuiccManager.downloadSubscription()` with consent dialog |

## Contributing

Contributions are welcome! Please open an issue or pull request on [GitHub](https://github.com/clickesim/expo-esim-utils).

## License

MIT
