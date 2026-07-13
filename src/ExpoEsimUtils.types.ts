/**
 * Detailed eSIM capability information for the current device.
 */
export type EsimCapability = {
  /** Whether the device hardware supports eSIM */
  isSupported: boolean;
  /** The platform: 'ios' or 'android' */
  platform: 'ios' | 'android';
  /**
   * How confident `isSupported` is:
   * - `'confirmed'`: verified directly by the OS (CoreTelephony with the
   *   carrier entitlement, or Android's `EuiccManager` — Android always
   *   reports `'confirmed'` since it has no entitlement wall).
   * - `'assumed'`: iOS only. The device model is a known eSIM-capable model,
   *   but `CTCellularPlanProvisioning` requires Apple's carrier-only
   *   entitlement to confirm it directly, which this library does not hold.
   * - `'unknown'`: iOS only, iPad only. The model identifier isn't
   *   recognized — most likely a new device released after this library's
   *   last update. `isSupported` conservatively reports `false`, but the
   *   device may actually support eSIM. Check for a library update.
   */
  confidence?: 'confirmed' | 'assumed' | 'unknown';
  /** Human-readable reason for the support status */
  reason: string;
  /** eUICC firmware version (Android only, when available) */
  osVersion?: string;
  /** Whether a SIM port is available for a new profile (Android 13+ only) */
  isSimPortAvailable?: boolean;
  /** Active cellular plans detected on the device */
  activePlans?: CellularPlan[];
  /** iOS hardware model identifier (e.g. "iPhone12,8"). iOS only. */
  deviceModel?: string;
};

/**
 * Information about an active cellular plan on the device.
 */
/**
 * Result of an eSIM installation attempt via `openEsimSetup()`.
 *
 * - `success`: eSIM profile was added successfully (Android only).
 * - `fail`: The installation failed (user cancelled or error).
 * - `unknown`: The result could not be determined.
 * - `settings_opened`: The native eSIM install screen was opened (iOS 17.4+ Universal Link
 *   or Android settings). The user completes installation outside the app.
 * - `universal_link_opened`: Android Universal Link handler was launched
 *   (`https://esimsetup.android.com/...`). The user completes installation in that flow.
 * - `unsupported`: Direct install not available on this OS version. Show QR/manual fallback.
 */
export type EsimSetupResult =
  | 'success'
  | 'fail'
  | 'unknown'
  | 'settings_opened'
  | 'universal_link_opened'
  | 'unsupported';

/**
 * Information about an active cellular plan on the device.
 */
export type CellularPlan = {
  /** SIM slot identifier */
  slot: string;
  /** Carrier display name */
  carrierName?: string;
  /** Mobile Country Code (MCC) */
  mobileCountryCode?: string;
  /** Mobile Network Code (MNC) */
  mobileNetworkCode?: string;
  /** ISO country code of the carrier */
  isoCountryCode?: string;
  /** Whether the plan allows VoIP (iOS only) */
  allowsVOIP?: boolean;
  /** Android subscription ID */
  subscriptionId?: number;
  /** Whether this is an embedded (eSIM) subscription (Android only) */
  isEmbedded?: boolean;
};
