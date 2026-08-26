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

  const handleSubmit = async (values: LoginValues, captchaToken: string | null) => {
    const user = await login({ ...values, captchaToken });
    navigate(user.mustChangePassword ? AUTH_ROUTES.forceChangePassword : from, { replace: true });
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Sign in</CardTitle>
        <CardDescription>Use your mobile number or email address.</CardDescription>
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
