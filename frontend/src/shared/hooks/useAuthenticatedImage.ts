import { useEffect, useState } from 'react';
import { apiGetBlob } from '@/services/apiClient';

/**
 * `<img src>` never sends the Authorization header, so an authenticated
 * image endpoint (avatar/logo/signature, all tenant-scoped - CR-023) can't
 * be linked to directly. Fetches the bytes through the normal authenticated
 * client and hands back an object URL instead. A 404 (no image set) is not
 * an error - it just means null.
 */
export function useAuthenticatedImage(url: string | null, refreshKey?: unknown): string | null {
  const [objectUrl, setObjectUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!url) {
      setObjectUrl(null);
      return;
    }
    let cancelled = false;
    let created: string | null = null;

    apiGetBlob(url)
      .then((blob) => {
        if (cancelled) return;
        created = URL.createObjectURL(blob);
        setObjectUrl(created);
      })
      .catch(() => {
        if (!cancelled) setObjectUrl(null);
      });

    return () => {
      cancelled = true;
      if (created) URL.revokeObjectURL(created);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url, refreshKey]);

  return objectUrl;
}
