import { requireNativeModule } from 'expo';

import type {
  CellularPlan,
  EsimCapability,
  EsimInstallResult,
  EsimSetupResult,
} from './ExpoEsimUtils.types';

type ExpoEsimUtilsModuleType = {
  isEsimSupported(): boolean;
  getEsimCapability(): EsimCapability;
  getActivePlans(): CellularPlan[];
  openEsimSetup(activationCode: string | null): Promise<EsimSetupResult>;
  /** Android only. Absent on iOS and in binaries built before 0.4.0. */
  installEsim?(activationCode: string | null): Promise<EsimInstallResult>;
};

export default requireNativeModule<ExpoEsimUtilsModuleType>('ExpoEsimUtils');
