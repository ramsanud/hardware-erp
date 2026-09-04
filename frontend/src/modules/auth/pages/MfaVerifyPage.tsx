import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import {
  Card, CardContent, CardDescription, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Label } from '@/shared/components/ui/label';
import { ApiError } from '@/shared/types/api';
import { AUTH_ROUTES } from '../constants';
import { useAuth } from '../hooks/AuthProvider';

/**
 * Second factor for an account that has already enrolled (CR-058). Accepts
 * either a live authenticator code or one of the one-time backup codes issued
 * at enrollment, so losing the phone is recoverable without an admin reset.
 */
export function MfaVerifyPage() {
  const { mfaToken, verifyMfa, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Landing here without a live challenge means the sign-in was never started
  // (or has expired) - there is nothing to verify against.
  if (!mfaToken && !isAuthenticated) {
    return <Navigate to={AUTH_ROUTES.login} replace />;
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const user = await verifyMfa(code.trim());
      navigate(user.mustChangePassword ? AUTH_ROUTES.forceChangePassword : '/dashboard',
        { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Verification failed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Card className="mx-auto w-full max-w-sm">
      <CardHeader>
        <CardTitle className="text-xl">Enter your verification code</CardTitle>
        <CardDescription>
          Open your authenticator app and enter the 6-digit code it shows. You can
          also use one of your backup codes.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {error ? (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : null}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="code">Verification code</Label>
            <Input
              id="code"
              inputMode="numeric"
              autoComplete="one-time-code"
              autoFocus
              required
              value={code}
              onChange={(e) => setCode(e.target.value)}
            />
          </div>
          <Button type="submit" className="w-full" loading={submitting}>
            Verify and sign in
          </Button>
        </form>
        <button
          type="button"
          onClick={() => navigate(AUTH_ROUTES.login, { replace: true })}
          className="w-full text-sm text-muted-foreground underline-offset-4 hover:underline"
        >
          Start over
        </button>
      </CardContent>
    </Card>
  );
}
