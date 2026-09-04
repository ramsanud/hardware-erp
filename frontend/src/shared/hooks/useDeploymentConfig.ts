import { useEffect, useState } from 'react';
import { deploymentConfigFallback, loadDeploymentConfig } from '@/services/deploymentConfig';
import type { DeploymentConfigResponse } from '@/shared/types/deployment';

export interface DeploymentConfigState extends DeploymentConfigResponse {
  /**
   * False until the server has answered. Gate anything that must not appear on
   * the wrong deployment on this, not on the value alone.
   *
   * The fallback while unresolved is the hosted product, so `billingEnabled`
   * is optimistically true for one render. Rendering the upgrade card on that
   * would flash "Upgrade to PRO - Rs 999/month" at a self-hosted client who
   * bought the software outright, which is precisely what CR-059 exists to
   * prevent. Waiting one frame costs nothing by comparison.
   */
  resolved: boolean;
}

/**
 * CR-059 - reads the cached deployment config.
 *
 * Deliberately NOT built on useAsyncData: there is no error state worth
 * surfacing (deploymentConfig.ts falls back rather than rejecting) and the
 * value is server-fixed and cached for the page's lifetime, so the usual
 * load/error/refetch machinery would be dead weight.
 */
export function useDeploymentConfig(): DeploymentConfigState {
  const [state, setState] = useState<DeploymentConfigState>({
    ...deploymentConfigFallback,
    resolved: false,
  });

  useEffect(() => {
    let active = true;
    loadDeploymentConfig().then((loaded) => {
      if (active) {
        setState({ ...loaded, resolved: true });
      }
    });
    return () => {
      active = false;
    };
  }, []);

  return state;
}
