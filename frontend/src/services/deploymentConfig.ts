import { apiGet } from '@/services/apiClient';
import type { DeploymentConfigResponse } from '@/shared/types/deployment';

/**
 * CR-059 - which installation this build is running against.
 *
 * Backend: config/DeploymentConfigController.java (public, unauthenticated).
 *
 * Fetched once per page load and cached as a promise, not per component. The
 * answer cannot change while the tab is open - it is fixed by how the server
 * was started - so re-requesting it from every consumer would be pure noise on
 * a shop's connection.
 *
 * The frontend is built ONCE and shipped to both deployments (the self-hosted
 * Docker image and the hosted CDN build come from the same source), so this
 * has to be a runtime question. A Vite env var would be baked in at build time
 * and would be wrong for one of the two.
 */

/** Assumed while the real answer is in flight, and after a failed request. */
const FALLBACK: DeploymentConfigResponse = {
  mode: 'CLOUD',
  selfHosted: false,
  billingEnabled: true,
  installationName: '',
};

let inFlight: Promise<DeploymentConfigResponse> | null = null;

export function loadDeploymentConfig(): Promise<DeploymentConfigResponse> {
  if (!inFlight) {
    inFlight = apiGet<DeploymentConfigResponse>('/v1/deployment-config').catch(() => {
      // Never block the app on this. An older backend that predates CR-059
      // returns 404 here, and a shop mid-update should still be able to sign
      // in and raise an invoice - so fall back to the hosted defaults, which
      // are what every install behaved as before this endpoint existed.
      //
      // Cleared so a later render retries rather than caching the failure for
      // the lifetime of the tab.
      inFlight = null;
      return FALLBACK;
    });
  }
  return inFlight;
}

export const deploymentConfigFallback = FALLBACK;
