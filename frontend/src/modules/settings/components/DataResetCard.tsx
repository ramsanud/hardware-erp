import { useState } from 'react';
import { AlertTriangle, Loader2, RotateCcw } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import {
  Card, CardContent, CardHeader, CardTitle, CardDescription,
} from '@/shared/components/ui/card';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { useToast } from '@/modules/auth/hooks/useToast';
import { authService } from '@/modules/auth/services/authService';
import { TurnstileWidget } from '@/modules/auth/components/TurnstileWidget';
import { dataResetService } from '../services/dataResetService';
import type { DataResetPreviewResponse, DataResetResponse } from '../types';

/**
 * CR-067 - the Danger zone. Rendered only for a role holding DATA_RESET, which
 * by default means the owner alone.
 *
 * Two independent confirmations, and the reason for two rather than one is
 * worth keeping in view: Turnstile is inactive unless an install configures its
 * keys, so a CAPTCHA-only gate would quietly become no gate at all on exactly
 * the installs least likely to have a backup. The typed shop name is the half
 * that cannot be switched off.
 *
 * The counts come from the server every time the dialog opens. "342 invoices"
 * is a decision an owner can weigh; "your data" is not.
 */
export function DataResetCard({ onResetComplete }: { onResetComplete?: () => void }) {
  const toast = useToast();

  const [open, setOpen] = useState(false);
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [preview, setPreview] = useState<DataResetPreviewResponse | null>(null);
  const [phrase, setPhrase] = useState('');
  const [siteKey, setSiteKey] = useState<string | null>(null);
  const [captchaToken, setCaptchaToken] = useState<string | null>(null);
  const [captchaReset, setCaptchaReset] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<DataResetResponse | null>(null);

  /** Mirrors DataResetServiceImpl.normalise - the same tolerance, so the button never enables on a phrase the server will reject, or stays disabled on one it would accept. */
  const normalise = (value: string) => value.trim().replace(/\s+/g, ' ').toLowerCase();

  const captchaRequired = Boolean(preview?.captchaRequired && siteKey);
  const phraseMatches = Boolean(preview) && normalise(phrase) === normalise(preview!.shopName);
  const canConfirm = phraseMatches && (!captchaRequired || Boolean(captchaToken));

  const openDialog = async () => {
    setOpen(true);
    setPhrase('');
    setCaptchaToken(null);
    setError(null);
    setResult(null);
    setPreview(null);
    setLoadingPreview(true);
    try {
      // The site key is public and served by the same endpoint the sign-in
      // page uses; the secret never leaves the server.
      const [counts, captcha] = await Promise.all([
        dataResetService.preview(),
        authService.captchaConfig().catch(() => null),
      ]);
      setPreview(counts);
      setSiteKey(captcha?.enabled ? captcha.siteKey ?? null : null);
    } catch (caught) {
      setError('Could not work out what would be deleted. Nothing has been changed.');
      toast.error(caught, 'Could not load the reset preview.');
    } finally {
      setLoadingPreview(false);
    }
  };

  const handleConfirm = async () => {
    if (!canConfirm || !preview) return;
    setSubmitting(true);
    setError(null);
    try {
      const response = await dataResetService.reset({
        confirmationPhrase: phrase,
        captchaToken: captchaRequired ? captchaToken : null,
      });
      // Deliberately not closing the dialog and not reloading here. The
      // summary below is the only place the owner will ever see what was
      // removed, and an immediate reload would wipe it before it could be
      // read - the exact failure that had to be fixed twice in CR-032.
      setResult(response);
      toast.success(`${response.totalRecords} records deleted.`);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The reset did not run.');
      toast.error(caught, 'The reset did not run.');
      // A Turnstile token is single-use, so a failed attempt needs a fresh one.
      if (captchaRequired) {
        setCaptchaToken(null);
        setCaptchaReset((n) => n + 1);
      }
    } finally {
      setSubmitting(false);
    }
  };

  const closeAfterReset = () => {
    setOpen(false);
    onResetComplete?.();
  };

  return (
    <>
      <Card className="border-destructive/40">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-destructive">
            <AlertTriangle className="h-4 w-4" />
            Danger zone
          </CardTitle>
          <CardDescription>
            Permanently delete this shop&apos;s billing and stock history. Products, customers,
            suppliers, staff and settings are kept.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-muted-foreground">
            Useful for clearing trial or practice entries before you start trading for real.
            There is no undo — take a backup first.
          </p>
          <Button variant="destructive" onClick={openDialog} className="shrink-0">
            <RotateCcw className="mr-2 h-4 w-4" />
            Reset shop data
          </Button>
        </CardContent>
      </Card>

      <Dialog open={open} onOpenChange={(next) => !submitting && (next ? setOpen(true) : closeAfterReset())}>
        <DialogContent className="sm:max-w-lg">
          {result ? (
            <>
              <DialogHeader>
                <DialogTitle>Shop data reset</DialogTitle>
                <DialogDescription>
                  {result.totalRecords} records were permanently deleted.
                </DialogDescription>
              </DialogHeader>
              <ul className="max-h-64 space-y-1 overflow-y-auto text-sm">
                {result.groups.filter((group) => group.count > 0).map((group) => (
                  <li key={group.label} className="flex justify-between border-b py-1 last:border-0">
                    <span className="text-muted-foreground">{group.label}</span>
                    <span className="font-medium tabular-nums">{group.count}</span>
                  </li>
                ))}
              </ul>
              <DialogFooter>
                <Button onClick={closeAfterReset}>Done</Button>
              </DialogFooter>
            </>
          ) : (
            <>
              <DialogHeader>
                <DialogTitle className="text-destructive">Reset shop data</DialogTitle>
                <DialogDescription>
                  This cannot be undone. Everything listed below will be permanently deleted.
                </DialogDescription>
              </DialogHeader>

              {loadingPreview ? (
                <div className="flex items-center gap-2 py-6 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Working out what would be deleted…
                </div>
              ) : preview ? (
                <div className="space-y-4">
                  <ul className="max-h-48 space-y-1 overflow-y-auto text-sm">
                    {preview.groups.map((group) => (
                      <li key={group.label} className="flex justify-between border-b py-1 last:border-0">
                        <span className="text-muted-foreground">{group.label}</span>
                        <span className="font-medium tabular-nums">{group.count}</span>
                      </li>
                    ))}
                  </ul>

                  <Alert>
                    <AlertDescription className="text-xs">
                      <strong>Kept:</strong> products, customers, suppliers, categories, brands,
                      workers, coupons, staff accounts, roles and every shop setting. Your activity
                      log and security audit trail are kept too, so this reset stays on the record.
                    </AlertDescription>
                  </Alert>

                  <div className="space-y-2">
                    <label htmlFor="reset-phrase" className="text-sm font-medium">
                      Type <span className="font-semibold">{preview.shopName}</span> to confirm
                    </label>
                    <Input
                      id="reset-phrase"
                      value={phrase}
                      autoComplete="off"
                      onChange={(event) => setPhrase(event.target.value)}
                      placeholder={preview.shopName}
                      disabled={submitting}
                    />
                  </div>

                  {captchaRequired && siteKey ? (
                    <TurnstileWidget
                      siteKey={siteKey}
                      onToken={setCaptchaToken}
                      resetSignal={captchaReset}
                    />
                  ) : null}
                </div>
              ) : null}

              {error ? (
                <Alert variant="destructive">
                  <AlertDescription>{error}</AlertDescription>
                </Alert>
              ) : null}

              <DialogFooter>
                <Button variant="outline" onClick={() => setOpen(false)} disabled={submitting}>
                  Cancel
                </Button>
                <Button
                  variant="destructive"
                  onClick={handleConfirm}
                  disabled={!canConfirm}
                  loading={submitting}
                >
                  Delete {preview?.totalRecords ?? 0} records
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
