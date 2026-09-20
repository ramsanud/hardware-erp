import { useEffect } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Clock } from 'lucide-react';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { APP_NAME } from '@/shared/constants';
import { isAuthRoute } from '@/routes/ProtectedRoute';
import { AUTH_ROUTES } from '../constants';
import { useAuth } from '../hooks/AuthProvider';
import { AuthCard } from '../components/AuthCard';
import { LoginForm } from '../forms/LoginForm';
import type { LoginValues } from '../validation/schemas';

interface LocationState {
  from?: { pathname: string; search?: string; hash?: string };
  registered?: boolean;
}

export function LoginPage() {
  const { login, isAuthenticated, initialising, cancelPendingLogin, sessionExpired } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const state = location.state as LocationState | null;
  // BUG-FE-024: the query string is part of where the user was going.
  // Reducing the remembered location to its pathname silently dropped
  // ?categoryId=, ?tab=, every deep link the app builds - so a bounced user
  // signed in and landed on a *different* page than the one they asked for.
  /*
   * Defence in depth with ProtectedRoute's own guard: `from` is history
   * state, so it also survives a Back into this entry and can be set by
   * anything that navigates here. An auth screen arriving as the post-login
   * destination is always wrong - sending a freshly signed-in user to
   * /change-password or /login/verify strands them - so it degrades to the
   * dashboard rather than being trusted.
   */
  const from = state?.from && !isAuthRoute(state.from.pathname)
    ? `${state.from.pathname}${state.from.search ?? ''}${state.from.hash ?? ''}`
    : '/dashboard';
  const justRegistered = state?.registered === true;

  /*
   * BUG-FE-025 root cause. Reaching this screen is the user saying "start
   * again": they backed out of the second factor, clicked "Back to sign in"
   * from the password-reset flow, or were bounced here by an expired session.
   * Any mfaToken still held from a previous attempt is dead weight that keeps
   * /login/verify and /login/set-up-authenticator renderable - which is how
   * pressing Forward (or Android back-then-forward) dropped the user back into
   * an abandoned sign-in instead of onto the form they were looking at.
   *
   * Clearing it on mount makes those pages redirect to /login as they already
   * intend to, so the flow has exactly one live entry point at a time.
   */
  useEffect(() => {
    cancelPendingLogin();
  }, [cancelPendingLogin]);

  // Someone already signed in who lands here is bounced back, so the browser
  // back button after login does not show the form again.
  useEffect(() => {
    if (!initialising && isAuthenticated) {
      navigate(from, { replace: true });
    }
  }, [initialising, isAuthenticated, navigate, from]);

  // CR-058 - a correct password only clears the first factor. Where the user
  // goes next depends on whether they have an authenticator app set up yet.
  const handleSubmit = async (values: LoginValues, captchaToken: string | null) => {
    const { enrollmentRequired, signedIn } = await login({ ...values, captchaToken });

    // CR-060 - MFA is off on this server, so the session is already live.
    // Sending the user to a second-factor screen here would strand them: there
    // is no challenge to satisfy and no token for those pages to use.
    if (signedIn) {
      navigate(from, { replace: true });
      return;
    }

    navigate(enrollmentRequired ? AUTH_ROUTES.mfaEnroll : AUTH_ROUTES.mfaVerify,
      { replace: true, state: { from: state?.from } });
  };

  return (
    <AuthCard title="Welcome back!" description="Sign in to your account to continue">
      <div className="space-y-5">
        {justRegistered ? (
          <Alert>
            <AlertDescription>Your shop is ready. Sign in below to get started.</AlertDescription>
          </Alert>
        ) : null}
        {/* CR-100: the bounce from an expired session used to be silent, and a
            user mid-invoice read it as "the app logged me out for no reason". */}
        {sessionExpired ? (
          <Alert variant="warning" data-session-expired>
            <Clock aria-hidden />
            <AlertDescription className="text-foreground/80">
              Your session has expired. Sign in again to pick up where you left off.
            </AlertDescription>
          </Alert>
        ) : null}
        {/* "Forgot password?" sits beside Remember me inside the form, so it is not repeated here. */}
        <LoginForm onSubmit={handleSubmit} />
        <p className="text-center text-sm text-muted-foreground">
          New to {APP_NAME}?{' '}
          <Link
            to={AUTH_ROUTES.register}
            className="font-medium text-primary underline-offset-4 hover:underline"
          >
            Register your shop
          </Link>
        </p>
      </div>
    </AuthCard>
  );
}
