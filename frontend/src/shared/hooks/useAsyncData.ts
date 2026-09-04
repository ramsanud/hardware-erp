import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '@/shared/types/api';

interface State<T> {
  data: T | null;
  loading: boolean;
  error: ApiError | null;
}

/**
 * useAsyncList's sibling for endpoints that return a plain value rather than a
 * PageResponse - the CR-058 deleted-records lists, which are deliberately not
 * paginated. Same load / loading / error / refetch cycle and the same
 * stale-response guard, so three list pages do not each re-implement it and
 * get the guard subtly wrong.
 */
export function useAsyncData<T>(fetcher: () => Promise<T>, deps: unknown[]) {
  const [state, setState] = useState<State<T>>({ data: null, loading: true, error: null });
  const requestId = useRef(0);

  const load = useCallback(async () => {
    const current = ++requestId.current;
    setState((prev) => ({ ...prev, loading: true, error: null }));
    try {
      const data = await fetcher();
      if (current === requestId.current) {
        setState({ data, loading: false, error: null });
      }
    } catch (error) {
      if (current === requestId.current) {
        setState({
          data: null,
          loading: false,
          error: error instanceof ApiError
            ? error
            : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 }),
        });
      }
    }
    // fetcher identity is intentionally excluded; deps drive reloads.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => { void load(); }, [load]);

  return { ...state, reload: load };
}
