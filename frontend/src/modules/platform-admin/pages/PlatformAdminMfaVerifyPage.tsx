import { useEffect, useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import {
  Card, CardContent, CardDescription, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Label } from '@/shared/components/ui/label';
import { ApiError } from '@/shared/types/api';
import { PLATFORM_ADMIN_ROUTES } from '../constants';
import { usePlatformAdminAuth } from '../hooks/PlatformAdminAuthProvider';

/** Step 2 of 2 for an account that has already enrolled MFA. */
export function PlatformAdminMfaVerifyPage() {
  const { mfaToken, verifyMfa, isAuthenticated } = usePlatformAdminAuth();
  const navigate = useNavigate();
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (isAuthenticated) navigate(PLATFORM_ADMIN_ROUTES.dashboard, { replace: true });
  }, [isAuthenticated, navigate]);

  // No challenge in progress - a direct visit or a page reload (the mfaToken
  // lives in memory only). Back to the start.
  if (!mfaToken) {
    return <Navigate to={PLATFORM_ADMIN_ROUTES.login} replace />;
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await verifyMfa(code.trim());
      navigate(PLATFORM_ADMIN_ROUTES.dashboard, { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Verification failed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-dvh items-center justify-center bg-muted/30 p-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle className="text-xl">Verification code</CardTitle>
          <CardDescription>
            Enter the 6-digit code from your authenticator app, or a backup code.
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
              <Label htmlFor="code">Code</Label>
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
              Verify
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
