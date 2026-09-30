import { useEffect, useRef, useState, type FormEvent } from 'react';
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

/** CR-078. Cooldown shown while a just-sent email code is still fresh - mirrors EmailOtpService.RESEND_COOLDOWN_SECONDS. */
const RESEND_COOLDOWN_SECONDS = 60;

/**
 * Second factor for an account that has already enrolled (CR-058), or - since
 * CR-078 - for one that has no authenticator app and was sent a code by email
 * instead. Accepts a live authenticator code, a one-time backup code, or the
 * emailed one, whichever the challenge is for; the server decided which when
 * the password was checked, and this screen never has to ask.
 *
 * CR-081: the TOTP path is the screen the approved sign-in design shows, so
 * its copy and controls are the design's exactly - "Welcome back!", the
 * shield-prefixed code field, "Verify and sign in", an OR rule, and
 * "Start over" as a real outlined button rather than a text link. The EMAIL
 * path reuses the same shell and only swaps the description and adds a
 * resend action beneath the field, so it never reads as a second design.
 */
export function MfaVerifyPage() {
  const {
    mfaToken, verifyMfa, resendEmailCode, mfaMethod, emailHint, isAuthenticated,
  } = useAuth();
  const navigate = useNavigate();
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [resending, setResending] = useState(false);
  const [resendCooldown, setResendCooldown] = useState(0);
  const cooldownTimer = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => () => {
    if (cooldownTimer.current) clearInterval(cooldownTimer.current);
  }, []);

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

  const handleResend = async () => {
    setError(null);
    setResending(true);
    try {
      await resendEmailCode();
      setResendCooldown(RESEND_COOLDOWN_SECONDS);
      cooldownTimer.current = setInterval(() => {
        setResendCooldown((seconds) => {
          if (seconds <= 1 && cooldownTimer.current) clearInterval(cooldownTimer.current);
          return Math.max(0, seconds - 1);
        });
      }, 1000);
    } catch (err) {
      // A 429 here just means the earlier code is still live - not an error
      // worth alarming over, so the same code field still works.
      setError(err instanceof ApiError ? err.message : 'Could not resend the code.');
    } finally {
      setResending(false);
    }
  };

  const description = mfaMethod === 'EMAIL' && emailHint
    ? `We sent a 6-digit code to ${emailHint}`
    : 'Sign in to your account to continue';

  return (
    <AuthCard title="Welcome back!" description={description}>
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

          {mfaMethod === 'EMAIL' ? (
            <p className="text-center text-[13px] text-muted-foreground">
              Didn&apos;t get it?{' '}
              <button
                type="button"
                onClick={() => void handleResend()}
                disabled={resending || resendCooldown > 0}
                className="font-semibold text-primary underline-offset-2 hover:underline
                           disabled:cursor-not-allowed disabled:text-muted-foreground disabled:no-underline"
              >
                {resendCooldown > 0 ? `Resend code (${resendCooldown}s)` : 'Resend code'}
              </button>
            </p>
          ) : null}
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
