import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { ArrowRight, RotateCw, ShieldCheck } from 'lucide-react';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Label } from '@/shared/components/ui/label';
import { ApiError } from '@/shared/types/api';
import { AUTH_ROUTES } from '../constants';
import { AuthCard } from '../components/AuthCard';
import { useAuth } from '../hooks/AuthProvider';

/**
 * Second factor for an account that has already enrolled (CR-058). Accepts
 * either a live authenticator code or one of the one-time backup codes issued
 * at enrollment, so losing the phone is recoverable without an admin reset.
 *
 * CR-081: this is the screen the approved sign-in design shows, so its copy
 * and controls are the design's exactly - "Welcome back!", the shield-prefixed
 * code field, "Verify and sign in", an OR rule, and "Start over" as a real
 * outlined button rather than a text link.
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
    <AuthCard title="Welcome back!" description="Sign in to your account to continue">
      <div className="space-y-4">
        {error ? (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : null}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="code" className="text-[13.5px] font-medium">Verification code</Label>
            <div className="relative">
              <ShieldCheck
                className="pointer-events-none absolute left-3.5 top-1/2 h-[18px] w-[18px] -translate-y-1/2 text-primary"
                aria-hidden
              />
              <Input
                id="code"
                inputMode="numeric"
                autoComplete="one-time-code"
                placeholder="Enter 6-digit code"
                autoFocus
                required
                value={code}
                onChange={(e) => setCode(e.target.value)}
                className="h-12 rounded-xl pl-11 text-[15px] tracking-wide"
              />
            </div>
          </div>

          <Button
            type="submit"
            size="lg"
            loading={submitting}
            className="h-12 w-full rounded-xl text-[15px] font-semibold
                       shadow-[0_6px_16px_-8px_hsl(var(--primary)/0.6)]
                       transition-[transform,box-shadow] hover:-translate-y-px
                       hover:shadow-[0_10px_24px_-10px_hsl(var(--primary)/0.55)]"
          >
            <ArrowRight className="h-[18px] w-[18px]" aria-hidden />
            Verify and sign in
          </Button>
        </form>

        <div className="flex items-center gap-3.5 py-0.5" aria-hidden>
          <span className="h-px flex-1 bg-border" />
          <span className="text-[11.5px] font-semibold tracking-[0.12em] text-muted-foreground">OR</span>
          <span className="h-px flex-1 bg-border" />
        </div>

        <Button
          type="button"
          variant="outline"
          size="lg"
          onClick={() => navigate(AUTH_ROUTES.login, { replace: true })}
          className="h-12 w-full rounded-xl border-[1.5px] border-primary text-[15px] font-semibold text-primary
                     hover:bg-primary/5 hover:text-primary"
        >
          <RotateCw className="h-[18px] w-[18px]" aria-hidden />
          Start over
        </Button>
      </div>
    </AuthCard>
  );
}
