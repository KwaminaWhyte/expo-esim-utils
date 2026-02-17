import { Platform } from 'react-native';

import ExpoEsimUtilsModule from './ExpoEsimUtilsModule';
import type { CellularPlan, EsimCapability } from './ExpoEsimUtils.types';

export type { CellularPlan, EsimCapability };

/**
 * Check if the current device supports eSIM.
 *
 * - **iOS**: Uses `CTCellularPlanProvisioning.supportsCellularPlan()`.
 *   No entitlement required — works for any app on iOS 12.0+.
 * - **Android**: Uses `EuiccManager.isEnabled()`.
 *   No permission required — works for any app on Android 9 (API 28)+.
 * - **Web**: Always returns `false`.
 *
 * This is a synchronous call (JSI) and returns instantly.
 *
 * @example
 * ```ts
 * import { isEsimSupported } from 'expo-esim-utils';
 *
 * if (isEsimSupported()) {
 *   console.log('This device supports eSIM!');
 * }
 * ```
 */
export function isEsimSupported(): boolean {
  if (Platform.OS !== 'ios' && Platform.OS !== 'android') {
    return false;
  }
  return ExpoEsimUtilsModule.isEsimSupported();
}

/**
 * Get detailed eSIM capability information for the current device.
 *
 * Returns platform-specific details including the support status,
 * a human-readable reason, and any active cellular plans detected.
 *
 * @example
 * ```ts
 * import { getEsimCapability } from 'expo-esim-utils';
 *
 * const capability = getEsimCapability();
 * console.log(capability.isSupported); // true or false
 * console.log(capability.reason);      // "Device supports eSIM via CoreTelephony"
 * ```
 */
export function getEsimCapability(): EsimCapability {
  if (Platform.OS !== 'ios' && Platform.OS !== 'android') {
    return {
      platform: 'ios',
      isSupported: false,
      reason: 'eSIM is not supported on this platform',
    };
  }
  return ExpoEsimUtilsModule.getEsimCapability();
}

/**
 * Get a list of active cellular plans on the device.
 *
 * - **iOS**: Uses `CTTelephonyNetworkInfo.serviceSubscriberCellularProviders`.
 *   Returns carrier name, MCC, MNC, and VoIP capability per SIM slot.
 * - **Android**: Uses `SubscriptionManager.getActiveSubscriptionInfoList()`.
 *   Returns carrier name, MCC, MNC, country code, and whether the SIM is embedded (eSIM).
 *   Note: On Android 10+, the `READ_PHONE_STATE` permission is needed for full details.
 *   Without it, this returns an empty array (does not crash).
 *
 * @example
 * ```ts
 * import { getActivePlans } from 'expo-esim-utils';
 *
 * const plans = getActivePlans();
 * for (const plan of plans) {
 *   console.log(`${plan.carrierName} (slot ${plan.slot})`);
 * }
 * ```
 */
export function getActivePlans(): CellularPlan[] {
  if (Platform.OS !== 'ios' && Platform.OS !== 'android') {
    return [];
  }
  return ExpoEsimUtilsModule.getActivePlans();
}

/**
 * Open the system eSIM setup / management screen.
 *
 * - **iOS 17.4+** with activation code: Opens `cellular-setup://` URL for
 *   direct eSIM activation. Falls back to cellular settings if URL isn't supported.
 * - **iOS < 17.4** or no activation code: Opens the cellular data settings page.
 * - **Android 9+**: Opens `EuiccManager.ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS`.
 *   If an activation code is provided, it's copied to the clipboard.
 *
 * The activation code should be in LPA format: `LPA:1$<SMDP_ADDRESS>$<MATCHING_ID>`
 *
 * @param activationCode - Optional eSIM activation code in LPA format.
 * @returns `true` if the setup screen was opened successfully, `false` otherwise.
 *
 * @example
 * ```ts
 * import { openEsimSetup } from 'expo-esim-utils';
 *
 * // Open eSIM settings
 * await openEsimSetup();
 *
 * // Open with activation code for direct install (iOS 17.4+)
 * await openEsimSetup('LPA:1$smdp.example.com$MATCHING_ID');
 * ```
 */
export async function openEsimSetup(activationCode?: string): Promise<boolean> {
  if (Platform.OS !== 'ios' && Platform.OS !== 'android') {
    return false;
  }
  return ExpoEsimUtilsModule.openEsimSetup(activationCode ?? null);
}
