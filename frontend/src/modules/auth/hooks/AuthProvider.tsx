import {
  createContext, useCallback, useContext, useEffect, useMemo, useRef, useState,
} from 'react';
import type { ReactNode } from 'react';
import { setSessionExpiredHandler } from '@/services/apiClient';
import { tokenStorage } from '@/services/tokenStorage';
import { setThemeScope } from '@/theme/themeScope';
import { authService } from '../services/authService';
import type {
  LoginRequest, LoginResponse, MfaEnrollResponse, UserResponse,
} from '../types';

interface AuthContextValue {
  user: UserResponse | null;
  /** True until the startup refresh attempt settles. */
  initialising: boolean;
  isAuthenticated: boolean;
  mustChangePassword: boolean;
  /**
   * CR-058. Set once the password check passes and cleared once MFA is
   * satisfied - it is never a session by itself.
   */
  mfaToken: string | null;
  enrollmentRequired: boolean;
  /**
   * `signedIn` is true only when the server has MFA disabled (CR-060) and the
   * session is already live, so the caller must go straight to the app instead
   * of routing to a second-factor screen that would have nothing to verify.
   */
  login: (body: LoginRequest) => Promise<{ enrollmentRequired: boolean; signedIn: boolean }>;
  verifyMfa: (code: string) => Promise<UserResponse>;
  enrollMfa: () => Promise<MfaEnrollResponse>;
  confirmMfaEnroll: (code: string) => Promise<{ user: UserResponse; backupCodes: string[] }>;
  logout: () => Promise<void>;
  logoutAll: () => Promise<void>;
  refreshUser: () => Promise<void>;
  hasPermission: (permission: string) => boolean;
  hasAnyPermission: (...permissions: string[]) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [initialising, setInitialising] = useState(true);
  const [mustChangePassword, setMustChangePassword] = useState(false);
  const [mfaToken, setMfaToken] = useState<string | null>(null);
  const [enrollmentRequired, setEnrollmentRequired] = useState(false);
  const bootstrapped = useRef(false);

  const clearSession = useCallback(() => {
    tokenStorage.clear();
    setUser(null);
    setMustChangePassword(false);
    setMfaToken(null);
    setEnrollmentRequired(false);
    // CR-034: theme/appearance prefs are scoped per user id (see theme/themeScope.ts) - drop back to the shared "guest" scope so the next sign-in on this browser never inherits this user's look.
    setThemeScope(null);
  }, []);

  /**
   * The access token lives in memory only, so a page reload loses it. The
   * HttpOnly refresh cookie survives, so a silent refresh restores the session
   * without asking the user to sign in again.
   */
  useEffect(() => {
    if (bootstrapped.current) return;
    bootstrapped.current = true;

    void (async () => {
      try {
        const result = await authService.refresh();
        tokenStorage.set(result.accessToken);
        setUser(result.user);
        setMustChangePassword(result.mustChangePassword);
        setThemeScope(result.user.id);
      } catch {
        // No usable cookie. Expected on a first visit and after logout.
        clearSession();
      } finally {
        setInitialising(false);
      }
    })();
  }, [clearSession]);

  // A refresh failure mid-session must drop the user back to the login screen.
  useEffect(() => setSessionExpiredHandler(clearSession), [clearSession]);

  /** Applies a completed session. Shared by verifyMfa and confirmMfaEnroll. */
  const applySession = useCallback((session: LoginResponse) => {
    tokenStorage.set(session.accessToken);
    setUser(session.user);
    setMustChangePassword(session.mustChangePassword);
    setThemeScope(session.user.id);
    setMfaToken(null);
    setEnrollmentRequired(false);
    return session.user;
  }, []);

  /**
   * CR-058 - clears the first factor only. The caller routes to the enroll
   * or the verify screen depending on enrollmentRequired; no session exists
   * until one of those completes.
   */
  const login = useCallback(async (body: LoginRequest) => {
    const challenge = await authService.login(body);

    // CR-060 - the server has MFA switched off, so the password alone
    // completed sign-in and this response carries a finished session. Apply it
    // exactly as verifyMfa would; from here on the app cannot tell which route
    // the session arrived by, which is the point.
    if (challenge.session) {
      await applySession(challenge.session);
      return { enrollmentRequired: false, signedIn: true };
    }

    setMfaToken(challenge.mfaToken);
    setEnrollmentRequired(challenge.enrollmentRequired);
    return { enrollmentRequired: challenge.enrollmentRequired, signedIn: false };
  }, [applySession]);

  const verifyMfa = useCallback(async (code: string) => {
    if (!mfaToken) throw new Error('No verification in progress. Please sign in again.');
    return applySession(await authService.verifyMfa(mfaToken, code));
  }, [mfaToken, applySession]);

  const enrollMfa = useCallback(async () => {
    if (!mfaToken) throw new Error('No verification in progress. Please sign in again.');
    return authService.enrollMfa(mfaToken);
  }, [mfaToken]);

  const confirmMfaEnroll = useCallback(async (code: string) => {
    if (!mfaToken) throw new Error('No verification in progress. Please sign in again.');
    const result = await authService.confirmMfaEnroll(mfaToken, code);
    return { user: applySession(result.session), backupCodes: result.backupCodes };
  }, [mfaToken, applySession]);

  const logout = useCallback(async () => {
    try {
      await authService.logout();
    } finally {
      // Local state clears even if the call fails, so the user is never stuck
      // looking at a session they believe they ended.
      clearSession();
    }
  }, [clearSession]);

  const logoutAll = useCallback(async () => {
    try {
      await authService.logoutAll();
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const refreshUser = useCallback(async () => {
    const current = await authService.me();
    setUser(current);
    setMustChangePassword(current.mustChangePassword);
  }, []);

  const hasPermission = useCallback(
    (permission: string) => user?.permissions.includes(permission) ?? false,
    [user],
  );

  const hasAnyPermission = useCallback(
    (...permissions: string[]) => permissions.some((p) => user?.permissions.includes(p) ?? false),
    [user],
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      initialising,
      isAuthenticated: user !== null,
      mustChangePassword,
      mfaToken,
      enrollmentRequired,
      login,
      verifyMfa,
      enrollMfa,
      confirmMfaEnroll,
      logout,
      logoutAll,
      refreshUser,
      hasPermission,
      hasAnyPermission,
    }),
    [user, initialising, mustChangePassword, mfaToken, enrollmentRequired, login,
      verifyMfa, enrollMfa, confirmMfaEnroll, logout, logoutAll, refreshUser,
      hasPermission, hasAnyPermission],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
