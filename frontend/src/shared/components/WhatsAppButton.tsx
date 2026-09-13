import { useRef, useState, type ComponentProps } from 'react';
import { MessageCircle } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { useToast } from '@/modules/auth/hooks/useToast';
import type { WhatsAppLinkResponse } from '@/modules/notification/services/whatsAppLinkService';

type ButtonProps = ComponentProps<typeof Button>;

export interface WhatsAppButtonProps {
  /** Fetches the ready-made link. Called lazily - on hover/focus to warm up, and on click. */
  fetchLink: () => Promise<WhatsAppLinkResponse>;
  /** Visible label. Hidden below `sm` unless `alwaysShowLabel`; the icon and aria-label remain. */
  label?: string;
  /** Who this opens a chat with - becomes "Open WhatsApp for {recipient}" for screen readers. */
  recipient?: string;
  /** Why the button is disabled, shown as a tooltip and read by assistive tech. */
  disabledReason?: string;
  alwaysShowLabel?: boolean;
  variant?: ButtonProps['variant'];
  size?: ButtonProps['size'];
  className?: string;
  disabled?: boolean;
}

/**
 * CR-080 - one click opens the official WhatsApp app or WhatsApp Web on the
 * customer's own chat, with the message already typed. The person reads it
 * and presses Send. This component never sends anything and never posts
 * anything: it fetches a wa.me link and opens it.
 *
 * Why the link is fetched on hover/focus as well as on click: browsers only
 * allow `window.open` inside a short window after a real user gesture, and a
 * network round-trip on click eats into it (Safari in particular is strict).
 * Warming the fetch on `pointerenter`/`focus` means the click usually finds
 * the link already resolved and opens the window synchronously. When it does
 * not, the fetch on click still completes well within Chromium's and
 * Firefox's activation window.
 *
 * Deliberately no confirmation dialog: the recipient is visible on the page
 * beside the button, and WhatsApp itself - with the text sitting unsent in
 * the box - is the review step.
 */
export function WhatsAppButton({
  fetchLink,
  label = 'WhatsApp',
  recipient,
  disabledReason,
  alwaysShowLabel = false,
  variant = 'outline',
  size,
  className,
  disabled = false,
}: WhatsAppButtonProps) {
  const toast = useToast();
  const [opening, setOpening] = useState(false);
  // One in-flight promise shared by warm-up and click, so a hover followed
  // by a click never fetches twice and a stale link is never cached.
  const pending = useRef<Promise<WhatsAppLinkResponse> | null>(null);

  const resolveLink = () => {
    if (!pending.current) {
      pending.current = fetchLink().catch((error: unknown) => {
        pending.current = null;
        throw error;
      });
    }
    return pending.current;
  };

  const warmUp = () => {
    if (disabled || opening) return;
    // Errors surface on the click, where a toast makes sense; a hover
    // must never toast.
    void resolveLink().catch(() => undefined);
  };

  const open = async () => {
    if (disabled || opening) return;
    setOpening(true);
    try {
      const link = await resolveLink();
      // A fresh link next time: the balance or the message may have changed.
      pending.current = null;
      window.open(link.url, '_blank', 'noopener,noreferrer');
    } catch (caught) {
      toast.error(caught, 'Unable to prepare the WhatsApp message.');
    } finally {
      setOpening(false);
    }
  };

  const isDisabled = disabled || Boolean(disabledReason);
  // "Open WhatsApp reminder for Ravi Kumar" - the label is part of the name, so
  // two buttons on one page are never announced identically.
  const action = label === 'WhatsApp' ? 'Open WhatsApp' : `Open ${label}`;
  const ariaLabel = recipient ? `${action} for ${recipient}` : action;

  return (
    <Button
      type="button"
      variant={variant}
      size={size}
      className={className}
      onClick={() => void open()}
      onPointerEnter={warmUp}
      onFocus={warmUp}
      loading={opening}
      disabled={isDisabled}
      aria-label={ariaLabel}
      aria-disabled={isDisabled || undefined}
      title={disabledReason ?? ariaLabel}
      data-testid="whatsapp-button"
    >
      <MessageCircle className="h-4 w-4" aria-hidden />
      <span className={alwaysShowLabel ? undefined : 'hidden sm:inline'}>{label}</span>
    </Button>
  );
}
