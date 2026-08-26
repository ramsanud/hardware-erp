import { useRef, useState } from 'react';
import { ArrowRight, Check, FileText, ShieldCheck } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Checkbox } from '@/shared/components/ui/checkbox';
import { LegalDocumentDialog } from './LegalDocumentDialog';
import {
  LEGAL_LAST_UPDATED, PRIVACY_VERSION, PrivacyContent, TERMS_VERSION, TermsContent,
} from './LegalContent';

type DocId = 'terms' | 'privacy';

export interface ConsentState {
  termsViewed: boolean;
  privacyViewed: boolean;
  termsAccepted: boolean;
  marketingConsent: boolean;
}

export const EMPTY_CONSENT: ConsentState = {
  // Nothing is pre-selected. A pre-ticked consent box is not consent.
  termsViewed: false,
  privacyViewed: false,
  termsAccepted: false,
  marketingConsent: false,
};

export function canAcceptTerms(c: ConsentState) {
  return c.termsViewed && c.privacyViewed;
}

interface ConsentSectionProps {
  value: ConsentState;
  onChange: (next: ConsentState) => void;
  /** Validation message for the required agreement, from the form resolver. */
  error?: string;
}

/**
 * The review-and-agree step of registration.
 *
 * The final checkbox stays disabled until both required documents have been
 * opened. That is a nudge toward actually reading them, and it is worth being
 * precise about what it is not: opening a dialog is not proof that anyone read
 * anything, and this code does not pretend otherwise. It raises the floor from
 * "ticked a box next to a link" without inventing evidence.
 *
 * Marketing sits in its own block, visually quieter, never pre-selected, and
 * has no bearing on whether the account can be created - bundling it into the
 * required agreement is the dark pattern this layout exists to avoid.
 */
export function ConsentSection({ value, onChange, error }: ConsentSectionProps) {
  const [open, setOpen] = useState<DocId | null>(null);
  // Focus goes back to the button that opened the dialog, not to the top of
  // the form - Radix restores it to the trigger, so the trigger must persist.
  const triggerRefs = {
    terms: useRef<HTMLButtonElement>(null),
    privacy: useRef<HTMLButtonElement>(null),
  };

  const set = (patch: Partial<ConsentState>) => onChange({ ...value, ...patch });
  const ready = canAcceptTerms(value);

  const documents = [
    {
      id: 'terms' as const,
      icon: FileText,
      title: 'Terms & Conditions',
      version: TERMS_VERSION,
      viewed: value.termsViewed,
    },
    {
      id: 'privacy' as const,
      icon: ShieldCheck,
      title: 'Privacy Policy',
      version: PRIVACY_VERSION,
      viewed: value.privacyViewed,
    },
  ];

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h2 className="text-base font-semibold">Review before creating your account</h2>
        <p className="text-sm text-muted-foreground">
          Please open and review the documents below. They explain how your account and
          your shop&rsquo;s information are handled.
        </p>
      </div>

      {/* ---- Required documents ---- */}
      <section className="space-y-3" aria-labelledby="consent-required-heading">
        <h3 id="consent-required-heading"
            className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          Required to create your account
        </h3>

        <ul className="space-y-2">
          {documents.map((doc) => (
            <li key={doc.id}>
              <div className="flex items-center gap-3 rounded-lg border p-3">
                <doc.icon className="h-5 w-5 shrink-0 text-muted-foreground" aria-hidden />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{doc.title}</p>
                  <p className="text-xs text-muted-foreground">
                    Version {doc.version} · Updated {LEGAL_LAST_UPDATED}
                  </p>
                </div>

                {/* Reviewed state carries an icon and a word, not colour alone. */}
                {doc.viewed ? (
                  <span className="hidden items-center gap-1 text-xs font-medium text-success sm:inline-flex">
                    <Check className="h-3.5 w-3.5" aria-hidden />
                    Reviewed
                  </span>
                ) : null}

                <Button
                  ref={triggerRefs[doc.id]}
                  type="button"
                  variant={doc.viewed ? 'ghost' : 'outline'}
                  size="sm"
                  className="shrink-0"
                  onClick={() => setOpen(doc.id)}
                >
                  {doc.viewed ? 'View again' : 'View'}
                  <ArrowRight className="h-3.5 w-3.5" aria-hidden />
                  <span className="sr-only"> {doc.title}</span>
                </Button>
              </div>
              {doc.viewed ? (
                <p className="mt-1 pl-3 text-xs text-success sm:hidden">
                  <Check className="mr-1 inline h-3 w-3" aria-hidden />
                  Reviewed
                </p>
              ) : null}
            </li>
          ))}
        </ul>
      </section>

      {/* ---- Optional ---- */}
      <section className="space-y-3" aria-labelledby="consent-optional-heading">
        <h3 id="consent-optional-heading"
            className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          Optional
        </h3>
        <div className="flex items-start gap-3 rounded-lg border border-dashed p-3">
          <Checkbox
            id="marketingConsent"
            className="mt-0.5"
            checked={value.marketingConsent}
            onCheckedChange={(next) => set({ marketingConsent: next === true })}
            aria-describedby="marketingConsent-hint"
          />
          <div className="space-y-1">
            <label htmlFor="marketingConsent" className="cursor-pointer text-sm leading-snug">
              Send me product updates, feature announcements and promotional
              communications from Hardware ERP.
            </label>
            <p id="marketingConsent-hint" className="text-xs text-muted-foreground">
              Entirely optional — you can create your account without this, and
              unsubscribe at any time.
            </p>
          </div>
        </div>
      </section>

      {/* ---- Required agreement ---- */}
      <section className="space-y-3" aria-labelledby="consent-agreement-heading">
        <h3 id="consent-agreement-heading"
            className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          Required agreement
        </h3>

        <div className={`rounded-lg border p-4 ${error ? 'border-destructive' : 'bg-muted/40'}`}>
          <div className="flex items-start gap-3">
            <Checkbox
              id="termsAccepted"
              className="mt-0.5"
              checked={value.termsAccepted}
              disabled={!ready}
              onCheckedChange={(next) => set({ termsAccepted: next === true })}
              aria-describedby={error ? 'termsAccepted-error' : 'termsAccepted-hint'}
              aria-invalid={Boolean(error)}
            />
            <div className="space-y-1">
              <label
                htmlFor="termsAccepted"
                className={`text-sm leading-snug ${ready ? 'cursor-pointer' : 'cursor-not-allowed opacity-70'}`}
              >
                I have read and agree to the <strong>Terms &amp; Conditions</strong> and
                acknowledge the <strong>Privacy Policy</strong>.
              </label>

              {error ? (
                <p id="termsAccepted-error" role="alert" className="text-sm text-destructive">
                  {error}
                </p>
              ) : (
                <p id="termsAccepted-hint" className="text-xs text-muted-foreground">
                  {ready
                    ? `Terms version ${TERMS_VERSION} · Privacy version ${PRIVACY_VERSION}`
                    : 'Open both documents above to enable this.'}
                </p>
              )}
            </div>
          </div>
        </div>
      </section>

      <LegalDocumentDialog
        open={open === 'terms'}
        onOpenChange={(next) => { if (!next) setOpen(null); }}
        title="Terms & Conditions"
        subtitle="Please review the terms governing your use of Hardware ERP."
        version={TERMS_VERSION}
        lastUpdated={LEGAL_LAST_UPDATED}
        onAcknowledge={() => { set({ termsViewed: true }); setOpen(null); }}
      >
        <TermsContent />
      </LegalDocumentDialog>

      <LegalDocumentDialog
        open={open === 'privacy'}
        onOpenChange={(next) => { if (!next) setOpen(null); }}
        title="Privacy Policy"
        subtitle="How your account and your shop’s information are handled."
        version={PRIVACY_VERSION}
        lastUpdated={LEGAL_LAST_UPDATED}
        onAcknowledge={() => { set({ privacyViewed: true }); setOpen(null); }}
      >
        <PrivacyContent />
      </LegalDocumentDialog>
    </div>
  );
}
