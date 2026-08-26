import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError, type PageResponse } from '@/shared/types/api';

interface State<T> {
  data: PageResponse<T> | null;
  loading: boolean;
  error: ApiError | null;
}

/**
 * One place for the load / loading / error / refetch cycle so no page
 * re-implements it. Stale responses are discarded: with a debounced search box
 * a slow earlier request can otherwise land after a faster later one and
 * overwrite the correct results.
 */
export function useAsyncList<T>(
  fetcher: () => Promise<PageResponse<T>>,
  deps: unknown[],
) {
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
