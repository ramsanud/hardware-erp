import { useCallback, useState } from 'react';
import { asFeatureNotAvailable, type FeatureNotAvailableDetails } from '../lib/featureNotAvailable';

/**
 * CR-088 §12. Wrap any action that might hit a plan-gated API in
 * `guard(action)` - a FEATURE_NOT_AVAILABLE response opens the upgrade
 * dialog instead of propagating as a generic error toast; every other
 * error is re-thrown for the caller's own handling.
 */
export function useFeatureGate() {
  const [details, setDetails] = useState<FeatureNotAvailableDetails | null>(null);

  const guard = useCallback(async <T,>(action: () => Promise<T>): Promise<T | undefined> => {
    try {
      return await action();
    } catch (error) {
      const parsed = asFeatureNotAvailable(error);
      if (parsed) {
        setDetails(parsed);
        return undefined;
      }
      throw error;
    }
  }, []);

  const close = useCallback(() => setDetails(null), []);

  return { details, guard, close };
}
