import { useEffect } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import {
  Card, CardContent, CardDescription, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { AUTH_ROUTES } from '../constants';
import { useAuth } from '../hooks/AuthProvider';
import { LoginForm } from '../forms/LoginForm';
import type { LoginValues } from '../validation/schemas';

interface LocationState {
  from?: { pathname: string };
  registered?: boolean;
}

export function LoginPage() {
  const { login, isAuthenticated, initialising } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const state = location.state as LocationState | null;
  const from = state?.from?.pathname ?? '/dashboard';
  const justRegistered = state?.registered === true;

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
    <Card className="mx-auto w-full max-w-sm">
      <CardHeader>
        <CardTitle className="text-xl">Sign in</CardTitle>
        <CardDescription>
          Welcome back. Enter your credentials to continue.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {justRegistered ? (
          <Alert>
            <AlertDescription>Your shop is ready. Sign in below to get started.</AlertDescription>
          </Alert>
        ) : null}
        <LoginForm onSubmit={handleSubmit} />
        <div className="flex items-center justify-between text-sm">
          <Link
            to={AUTH_ROUTES.forgotPassword}
            className="text-primary underline-offset-4 hover:underline"
          >
            Forgot your password?
          </Link>
          <Link
            to={AUTH_ROUTES.register}
            className="text-primary underline-offset-4 hover:underline"
          >
            Register your shop
          </Link>
        </div>
      </CardContent>
    </Card>
  );
}
