/**
 * Detailed eSIM capability information for the current device.
 */
export type EsimCapability = {
  /** Whether the device hardware supports eSIM */
  isSupported: boolean;
  /** The platform: 'ios' or 'android' */
  platform: 'ios' | 'android';
  /** Human-readable reason for the support status */
  reason: string;
  /** eUICC firmware version (Android only, when available) */
  osVersion?: string;
  /** Whether a SIM port is available for a new profile (Android 13+ only) */
  isSimPortAvailable?: boolean;
  /** Active cellular plans detected on the device */
  activePlans?: CellularPlan[];
};

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
