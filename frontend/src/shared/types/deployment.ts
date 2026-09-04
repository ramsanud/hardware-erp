/**
 * CR-059 - which installation this browser is talking to.
 *
 * Mirrors backend config/DeploymentConfigResponse.java exactly.
 */

export type DeploymentMode = 'CLOUD' | 'SELF_HOSTED';

export interface DeploymentConfigResponse {
  /** CLOUD for the hosted SaaS, SELF_HOSTED for an on-premise Docker install. */
  mode: DeploymentMode;
  /** Convenience flag - true when mode is SELF_HOSTED. */
  selfHosted: boolean;
  /**
   * Whether subscription checkout and plan-upgrade prompts apply here. False
   * on a self-hosted licence: the client bought the software outright, so the
   * server refuses checkout with BILLING_NOT_APPLICABLE and the UI must not
   * offer it in the first place.
   */
  billingEnabled: boolean;
  /** Operator-set label for this installation. May be an empty string. */
  installationName: string;
}
