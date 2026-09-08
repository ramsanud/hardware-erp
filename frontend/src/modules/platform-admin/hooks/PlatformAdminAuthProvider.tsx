import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { setPlatformAdminSessionExpiredHandler } from '@/services/platformAdminApiClient';
import { platformAdminTokenStorage } from '@/services/platformAdminTokenStorage';
import { platformAdminAuthService } from '../services/platformAdminAuthService';
import type { PlatformAdminResponse } from '../types';

/**
 * Mirrors modules/auth/hooks/AuthProvider.tsx in shape, deliberately not in
 * code - a completely separate context so a platform-admin session and a
 * tenant session can be open in two tabs without either provider's state
 * ever touching the other's.
 */
interface PlatformAdminAuthContextValue {
  admin: PlatformAdminResponse | null;
  /** True until the startup refresh attempt settles - CR-065. */
  initialising: boolean;
  isAuthenticated: boolean;
  /** Set once /login succeeds and cleared once MFA is satisfied - never a session by itself. */
  mfaToken: string | null;
  enrollmentRequired: boolean;
  login: (email: string, password: string) => Promise<{ enrollmentRequired: boolean }>;
  verifyMfa: (code: string) => Promise<void>;
  enroll: () => Promise<{ otpAuthUri: string; qrCodePngBase64: string; secretBase32: string }>;
  confirmEnroll: (code: string) => Promise<string[]>;
  logout: () => Promise<void>;
}

const PlatformAdminAuthContext = createContext<PlatformAdminAuthContextValue | null>(null);

export function PlatformAdminAuthProvider({ children }: { children: ReactNode }) {
  const [admin, setAdmin] = useState<PlatformAdminResponse | null>(null);
  const [initialising, setInitialising] = useState(true);
  const [mfaToken, setMfaToken] = useState<string | null>(null);
  const [enrollmentRequired, setEnrollmentRequired] = useState(false);
  const bootstrapped = useRef(false);

  const clearSession = useCallback(() => {
    platformAdminTokenStorage.clear();
    setAdmin(null);
    setMfaToken(null);
    setEnrollmentRequired(false);
  }, []);

  /**
   * CR-065. The access token is in memory only, so a reload loses it - but
   * the HttpOnly refresh cookie survives, and this exchanges it for a new
   * session without asking the admin to sign in again.
   *
   * This is what fixes "a page reload signs the platform admin out". It fixes
   * it WITHOUT weakening the access token, which stays in memory exactly as
   * before; the durable half is the cookie, and JavaScript still cannot read
   * that. Mirrors the tenant AuthProvider's bootstrap.
   */
  useEffect(() => {
    if (bootstrapped.current) return;
    bootstrapped.current = true;

    void (async () => {
      try {
        const session = await platformAdminAuthService.refresh();
        platformAdminTokenStorage.set(session.accessToken);
        setAdmin(session.admin);
      } catch {
        // No usable cookie. Expected on a first visit and after signing out.
        clearSession();
      } finally {
        setInitialising(false);
      }
    })();
  }, [clearSession]);

  useEffect(() => setPlatformAdminSessionExpiredHandler(clearSession), [clearSession]);

  const login = useCallback(async (email: string, password: string) => {
    const challenge = await platformAdminAuthService.login({ email, password });
    setMfaToken(challenge.mfaToken);
    setEnrollmentRequired(challenge.enrollmentRequired);
    return { enrollmentRequired: challenge.enrollmentRequired };
  }, []);

  const verifyMfa = useCallback(async (code: string) => {
    if (!mfaToken) throw new Error('No MFA challenge in progress');
    const session = await platformAdminAuthService.verifyMfa({ mfaToken, code });
    platformAdminTokenStorage.set(session.accessToken);
    setAdmin(session.admin);
    setMfaToken(null);
  }, [mfaToken]);

  const enroll = useCallback(async () => {
    if (!mfaToken) throw new Error('No MFA challenge in progress');
    return platformAdminAuthService.enroll(mfaToken);
  }, [mfaToken]);

  const confirmEnroll = useCallback(async (code: string) => {
    if (!mfaToken) throw new Error('No MFA challenge in progress');
    const result = await platformAdminAuthService.confirmEnroll({ mfaToken, code });
    platformAdminTokenStorage.set(result.session.accessToken);
    setAdmin(result.session.admin);
    setMfaToken(null);
    return result.backupCodes;
  }, [mfaToken]);

  /** Never rejects - same contract as the tenant AuthProvider's logout (BUG-FE-010). */
  const logout = useCallback(async () => {
    try {
      await platformAdminAuthService.logout();
    } catch (error) {
      console.warn('[platform-admin] Sign-out call failed; clearing the local session anyway.', error);
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const value = useMemo<PlatformAdminAuthContextValue>(() => ({
    admin,
    initialising,
    isAuthenticated: admin !== null,
    mfaToken,
    enrollmentRequired,
    login,
    verifyMfa,
    enroll,
    confirmEnroll,
    logout,
  }), [admin, initialising, mfaToken, enrollmentRequired, login, verifyMfa, enroll,
    confirmEnroll, logout]);

  return (
    <PlatformAdminAuthContext.Provider value={value}>
      {children}
    </PlatformAdminAuthContext.Provider>
  );
}

export function usePlatformAdminAuth(): PlatformAdminAuthContextValue {
  const context = useContext(PlatformAdminAuthContext);
  if (!context) throw new Error('usePlatformAdminAuth must be used inside PlatformAdminAuthProvider');
  return context;
}
