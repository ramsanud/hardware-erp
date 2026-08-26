import {
  createContext, useCallback, useContext, useEffect, useMemo, useRef, useState,
} from 'react';
import type { ReactNode } from 'react';
import { setSessionExpiredHandler } from '@/services/apiClient';
import { tokenStorage } from '@/services/tokenStorage';
import { setThemeScope } from '@/theme/themeScope';
import { authService } from '../services/authService';
import type { LoginRequest, UserResponse } from '../types';

interface AuthContextValue {
  user: UserResponse | null;
  /** True until the startup refresh attempt settles. */
  initialising: boolean;
  isAuthenticated: boolean;
  mustChangePassword: boolean;
  login: (body: LoginRequest) => Promise<UserResponse>;
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
  const bootstrapped = useRef(false);

  const clearSession = useCallback(() => {
    tokenStorage.clear();
    setUser(null);
    setMustChangePassword(false);
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

  const login = useCallback(async (body: LoginRequest) => {
    const result = await authService.login(body);
    tokenStorage.set(result.accessToken);
    setUser(result.user);
    setMustChangePassword(result.mustChangePassword);
    setThemeScope(result.user.id);
    return result.user;
  }, []);

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
      login,
      logout,
      logoutAll,
      refreshUser,
      hasPermission,
      hasAnyPermission,
    }),
    [user, initialising, mustChangePassword, login, logout, logoutAll, refreshUser,
      hasPermission, hasAnyPermission],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
