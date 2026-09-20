import { useSyncExternalStore } from 'react';

function subscribe(onChange: () => void) {
  window.addEventListener('online', onChange);
  window.addEventListener('offline', onChange);
  return () => {
    window.removeEventListener('online', onChange);
    window.removeEventListener('offline', onChange);
  };
}

function read() {
  return typeof navigator === 'undefined' ? true : navigator.onLine;
}

/**
 * CR-100. `navigator.onLine`, kept current.
 *
 * "Online" here means the browser has a network interface up - a captive
 * portal or a dead backend still reads as online. That is fine for what it
 * gates: the offline banner, and the choice between "you are offline" and
 * "cannot reach the server" in ErrorState. Anything stronger (an actual
 * reachability probe) would be a request the app then has to explain.
 */
export function useOnlineStatus(): boolean {
  return useSyncExternalStore(subscribe, read, () => true);
}
