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
import { AUTH_ROUTES } from '../constants';
import { useAuth } from '../hooks/AuthProvider';

/**
 * First-login enrollment (CR-058). mfa_enabled stays false on the backend
 * until the code entered here is actually verified, so an interrupted
 * enrollment - tab closed, network drop - leaves the account exactly as it
 * was and simply asks again at the next sign-in.
 */
export function MfaEnrollPage() {
  const { mfaToken, enrollMfa, confirmMfaEnroll, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [qrCode, setQrCode] = useState<string | null>(null);
  const [secret, setSecret] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const [backupCodes, setBackupCodes] = useState<string[] | null>(null);
  const [mustChangePassword, setMustChangePassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loadingQr, setLoadingQr] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!mfaToken) return;
    enrollMfa()
      .then((result) => {
        setQrCode(result.qrCodePngBase64);
        setSecret(result.secretBase32);
      })
      .catch((err) => setError(
        err instanceof ApiError ? err.message : 'Could not start setting up two-factor authentication',
      ))
      .finally(() => setLoadingQr(false));
    // Only ever on mount for this challenge - re-running would issue a new
    // secret and invalidate the one already on screen.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mfaToken]);

  if (!mfaToken && !isAuthenticated) {
    return <Navigate to={AUTH_ROUTES.login} replace />;
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const result = await confirmMfaEnroll(code.trim());
      setMustChangePassword(result.user.mustChangePassword);
      setBackupCodes(result.backupCodes);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Verification failed');
    } finally {
      setSubmitting(false);
    }
  };

  if (backupCodes) {
    return (
      <Card className="mx-auto w-full max-w-md">
        <CardHeader>
          <CardTitle className="text-xl">Save your backup codes</CardTitle>
          <CardDescription>
            Each code signs you in once, in place of your authenticator app, if you
            lose access to it. Shown only this once - save them somewhere safe.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-2 gap-2 rounded-md border bg-muted/40 p-4 font-mono text-sm">
            {backupCodes.map((c) => <div key={c}>{c}</div>)}
          </div>
          <Button
            className="w-full"
            onClick={() => navigate(
              mustChangePassword ? AUTH_ROUTES.forceChangePassword : '/dashboard',
              { replace: true },
            )}
          >
            I've saved these codes
          </Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="mx-auto w-full max-w-sm">
      <CardHeader>
        <CardTitle className="text-xl">Set up two-factor authentication</CardTitle>
        <CardDescription>
          This protects your shop's data even if your password is stolen. Scan this
          QR code with an authenticator app (Google Authenticator, Authy, 1Password),
          then enter the 6-digit code it shows.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {error ? (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : null}
        {loadingQr ? (
          <div className="text-sm text-muted-foreground">Generating your QR code...</div>
        ) : qrCode ? (
          <div className="flex flex-col items-center gap-2">
            <img
              src={`data:image/png;base64,${qrCode}`}
              alt="Two-factor authentication QR code"
              className="h-48 w-48 rounded-md border"
            />
            {secret ? (
              <p className="text-center text-xs text-muted-foreground">
                Can't scan it? Enter this key manually:{' '}
                <span className="font-mono break-all">{secret}</span>
              </p>
            ) : null}
          </div>
        ) : null}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="code">6-digit code</Label>
            <Input
              id="code"
              inputMode="numeric"
              autoComplete="one-time-code"
              required
              value={code}
              onChange={(e) => setCode(e.target.value)}
            />
          </div>
          <Button type="submit" className="w-full" loading={submitting} disabled={loadingQr}>
            Confirm and continue
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
