import {
  createContext, useCallback, useContext, useEffect, useMemo, useRef, useState,
} from 'react';
import type { ReactNode } from 'react';
import { setSessionExpiredHandler } from '@/services/apiClient';
import { tokenStorage } from '@/services/tokenStorage';
import { setThemeScope } from '@/theme/themeScope';
import { authService } from '../services/authService';
import type {
  LoginRequest, LoginResponse, MfaEnrollResponse, OtpSentResponse, UserResponse,
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
  /** CR-078. How the pending challenge is verified - null while enrolling or before a login attempt. */
  mfaMethod: 'TOTP' | 'EMAIL' | null;
  /** CR-078. The masked address a sign-in code went to. Null unless mfaMethod is EMAIL. */
  emailHint: string | null;
  /**
   * `signedIn` is true only when the server has MFA disabled (CR-060) and the
   * session is already live, so the caller must go straight to the app instead
   * of routing to a second-factor screen that would have nothing to verify.
   */
  login: (body: LoginRequest) => Promise<{ enrollmentRequired: boolean; signedIn: boolean }>;
  /**
   * Abandons a half-finished sign-in. The password step leaves an mfaToken
   * behind that is not a session but IS enough for MfaVerifyPage/MfaEnrollPage
   * to render - so a user who backs out to /login and then presses Forward
   * lands back on the second-factor screen of an attempt they walked away
   * from. Dropping the challenge makes those pages redirect to /login again,
   * which is the only correct answer once the flow has been abandoned.
   */
  cancelPendingLogin: () => void;
  /**
   * CR-100. True when the last session ended because the server refused to
   * refresh it - not because the user signed out. The sign-in page reads it
   * to explain the bounce; a completed sign-in clears it.
   */
  sessionExpired: boolean;
  verifyMfa: (code: string) => Promise<UserResponse>;
  enrollMfa: () => Promise<MfaEnrollResponse>;
  confirmMfaEnroll: (code: string) => Promise<{ user: UserResponse; backupCodes: string[] }>;
  /** CR-078 - another code to the same address the current challenge already went to. */
  resendEmailCode: () => Promise<OtpSentResponse>;
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
  const [sessionExpired, setSessionExpired] = useState(false);
  const [mfaMethod, setMfaMethod] = useState<'TOTP' | 'EMAIL' | null>(null);
  const [emailHint, setEmailHint] = useState<string | null>(null);
  const bootstrapped = useRef(false);
  // Read inside the expiry handler, which is registered once and must not
  // capture a stale `user`. Written from an effect, never during render; the
  // handler only ever runs from a network callback, which is after commit.
  const signedIn = useRef(false);
  useEffect(() => { signedIn.current = user !== null; }, [user]);

  const clearSession = useCallback(() => {
    tokenStorage.clear();
    setUser(null);
    setMustChangePassword(false);
    setMfaToken(null);
    setEnrollmentRequired(false);
    setMfaMethod(null);
    setEmailHint(null);
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
  // CR-100: and tell them why, but only if there was a session to lose - the
  // startup refresh on a first visit fails the same way and is not an expiry.
  useEffect(() => setSessionExpiredHandler(() => {
    if (signedIn.current) setSessionExpired(true);
    clearSession();
  }), [clearSession]);

  /** Applies a completed session. Shared by verifyMfa and confirmMfaEnroll. */
  const applySession = useCallback((session: LoginResponse) => {
    setSessionExpired(false);
    tokenStorage.set(session.accessToken);
    setUser(session.user);
    setMustChangePassword(session.mustChangePassword);
    setThemeScope(session.user.id);
    setMfaToken(null);
    setEnrollmentRequired(false);
    setMfaMethod(null);
    setEmailHint(null);
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
    setMfaMethod(challenge.mfaMethod);
    setEmailHint(challenge.emailHint);
    return { enrollmentRequired: challenge.enrollmentRequired, signedIn: false };
  }, [applySession]);

  const cancelPendingLogin = useCallback(() => {
    setMfaToken(null);
    setEnrollmentRequired(false);
    setMfaMethod(null);
    setEmailHint(null);
  }, []);

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

  const resendEmailCode = useCallback(async () => {
    if (!mfaToken) throw new Error('No verification in progress. Please sign in again.');
    return authService.resendEmailCode(mfaToken);
  }, [mfaToken]);

  /**
   * Never rejects. Local state clears even if the call fails, so the user is
   * never stuck looking at a session they believe they ended - and every
   * caller navigates to the login screen immediately after awaiting this, so
   * a rethrow would strand them on the page they were trying to leave.
   *
   * A 401 here is the normal case, not an anomaly: change-password revokes
   * every session and bumps tokenVersion before this runs, so the access
   * token is already dead and the refresh cookie already cleared. There is
   * nothing left to report - the server did the revocation this call was
   * asking for (BUG-FE-010).
   */
  const logout = useCallback(async () => {
    try {
      await authService.logout();
    } catch (error) {
      console.warn('[auth] Sign-out call failed; clearing the local session anyway.', error);
    } finally {
      // A deliberate sign-out is never an expiry, even when the call itself
      // was refused with a 401 on the way out (CR-100).
      clearSession();
      setSessionExpired(false);
    }
  }, [clearSession]);

  /** Same contract as logout: clears locally and never rejects. */
  const logoutAll = useCallback(async () => {
    try {
      await authService.logoutAll();
    } catch (error) {
      console.warn('[auth] Sign-out-everywhere call failed; clearing the local session anyway.', error);
    } finally {
      // A deliberate sign-out is never an expiry, even when the call itself
      // was refused with a 401 on the way out (CR-100).
      clearSession();
      setSessionExpired(false);
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
      mfaMethod,
      emailHint,
      login,
      cancelPendingLogin,
      sessionExpired,
      verifyMfa,
      enrollMfa,
      confirmMfaEnroll,
      resendEmailCode,
      logout,
      logoutAll,
      refreshUser,
      hasPermission,
      hasAnyPermission,
    }),
    [user, initialising, mustChangePassword, mfaToken, enrollmentRequired, mfaMethod, emailHint, login,
      cancelPendingLogin, sessionExpired, verifyMfa, enrollMfa, confirmMfaEnroll, resendEmailCode, logout, logoutAll,
      refreshUser, hasPermission, hasAnyPermission],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
