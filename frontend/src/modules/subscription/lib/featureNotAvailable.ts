import { ApiError } from '@/shared/types/api';

/** Parsed body of a 403 FEATURE_NOT_AVAILABLE - see GlobalExceptionHandler.handleFeatureNotAvailable(). */
export interface FeatureNotAvailableDetails {
  featureKey: string;
  featureName: string;
  currentPlanCode: string;
  currentPlanName: string;
  requiredPlanCode: string | null;
  requiredPlanName: string | null;
}

/** Returns the parsed upgrade details when `error` is a 403 FEATURE_NOT_AVAILABLE from the API client, else null. */
export function asFeatureNotAvailable(error: unknown): FeatureNotAvailableDetails | null {
  if (!(error instanceof ApiError) || error.code !== 'FEATURE_NOT_AVAILABLE') {
    return null;
  }
  const fields = error.fieldErrors ?? {};
  if (!fields.featureKey || !fields.currentPlanCode) {
    return null;
  }
  return {
    featureKey: fields.featureKey,
    featureName: fields.featureName ?? fields.featureKey,
    currentPlanCode: fields.currentPlanCode,
    currentPlanName: fields.currentPlanName ?? fields.currentPlanCode,
    requiredPlanCode: fields.requiredPlanCode ?? null,
    requiredPlanName: fields.requiredPlanName ?? null,
  };
}
