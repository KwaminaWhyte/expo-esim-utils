import { requireNativeModule } from 'expo';

import type { CellularPlan, EsimCapability, EsimSetupResult } from './ExpoEsimUtils.types';

type ExpoEsimUtilsModuleType = {
  isEsimSupported(): boolean;
  getEsimCapability(): EsimCapability;
  getActivePlans(): CellularPlan[];
  openEsimSetup(activationCode: string | null): Promise<EsimSetupResult>;
};

export default requireNativeModule<ExpoEsimUtilsModuleType>('ExpoEsimUtils');
