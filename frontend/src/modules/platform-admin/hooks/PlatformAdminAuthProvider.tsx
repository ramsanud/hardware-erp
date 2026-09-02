import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
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
  const [mfaToken, setMfaToken] = useState<string | null>(null);
  const [enrollmentRequired, setEnrollmentRequired] = useState(false);

  const clearSession = useCallback(() => {
    platformAdminTokenStorage.clear();
    setAdmin(null);
    setMfaToken(null);
    setEnrollmentRequired(false);
  }, []);

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
    platformAdminTokenStorage.set(session.accessToken, session.refreshToken);
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
    platformAdminTokenStorage.set(result.session.accessToken, result.session.refreshToken);
    setAdmin(result.session.admin);
    setMfaToken(null);
    return result.backupCodes;
  }, [mfaToken]);

  const logout = useCallback(async () => {
    try {
      await platformAdminAuthService.logout(platformAdminTokenStorage.getRefreshToken());
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const value = useMemo<PlatformAdminAuthContextValue>(() => ({
    admin,
    isAuthenticated: admin !== null,
    mfaToken,
    enrollmentRequired,
    login,
    verifyMfa,
    enroll,
    confirmEnroll,
    logout,
  }), [admin, mfaToken, enrollmentRequired, login, verifyMfa, enroll, confirmEnroll, logout]);

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
