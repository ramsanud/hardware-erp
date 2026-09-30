import { useCallback, useRef } from 'react';

/**
 * CR-102. One key per attempted write. The key is created when the form
 * (or dialog) opens and stays the same across a failed or timed-out
 * submit, so a retry replays the same request on the server instead of
 * creating a second invoice, payment or purchase. Call `renew()` once the
 * write has succeeded, or when the form is reset for a genuinely new one.
 */
export function useIdempotencyKey() {
  const ref = useRef<string>(crypto.randomUUID());
  const renew = useCallback(() => {
    ref.current = crypto.randomUUID();
  }, []);
  const current = useCallback(() => ref.current, []);
  return { current, renew };
}
