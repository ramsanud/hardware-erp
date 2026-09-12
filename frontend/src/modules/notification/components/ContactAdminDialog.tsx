import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { ImagePlus, X } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter,
} from '@/shared/components/ui/dialog';
import { FormField } from '@/shared/components/FormField';
import { useToast } from '@/modules/auth/hooks/useToast';
import {
  notificationService, SCREENSHOT_MAX_BYTES, SCREENSHOT_TYPES,
} from '../services/notificationService';

const contactSchema = z.object({
  subject: z.string().trim().min(1, 'Subject is required').max(150),
  message: z.string().trim().min(1, 'Describe the issue').max(2000),
});
type ContactValues = z.infer<typeof contactSchema>;

interface ContactAdminDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/** Any signed-in user can report a problem with the app itself - not gated by any permission, unlike every business feature (CR-028). */
export function ContactAdminDialog({ open, onOpenChange }: ContactAdminDialogProps) {
  const toast = useToast();
  const [sending, setSending] = useState(false);
  const [screenshot, setScreenshot] = useState<File | null>(null);
  const [screenshotError, setScreenshotError] = useState<string | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const {
    register, handleSubmit, reset, formState: { errors },
  } = useForm<ContactValues>({
    resolver: zodResolver(contactSchema),
    defaultValues: { subject: '', message: '' },
  });

  // An object URL is a document-lifetime handle on the file's bytes, so it is
  // revoked whenever the chosen image changes or the dialog unmounts. Without
  // this, picking five screenshots in a row leaks all five.
  useEffect(() => {
    if (!screenshot) { setPreviewUrl(null); return undefined; }
    const url = URL.createObjectURL(screenshot);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [screenshot]);

  const clearScreenshot = () => {
    setScreenshot(null);
    setScreenshotError(null);
    // Resetting the input's value matters: without it, re-picking the SAME
    // file after removing it fires no change event and nothing happens.
    if (fileInput.current) fileInput.current.value = '';
  };

  /** Mirrors ImageValidation.validate() so the reporter is told before the upload, not after. */
  const chooseScreenshot = (file: File | undefined) => {
    if (!file) return;
    if (!SCREENSHOT_TYPES.includes(file.type)) {
      setScreenshot(null);
      setScreenshotError('Use a PNG, JPEG or WebP image.');
      return;
    }
    if (file.size > SCREENSHOT_MAX_BYTES) {
      setScreenshot(null);
      setScreenshotError('Image must be 2MB or smaller.');
      return;
    }
    setScreenshotError(null);
    setScreenshot(file);
  };

  const closeAndReset = () => {
    reset();
    clearScreenshot();
    onOpenChange(false);
  };

  const submit = handleSubmit(async (values) => {
    setSending(true);
    try {
      await notificationService.contactAdmin({ ...values, screenshot });
      toast.success("Your message was sent. We'll get back to you soon.");
      closeAndReset();
    } catch (caught) {
      toast.error(caught, 'Could not send your message. Please try again.');
    } finally {
      setSending(false);
    }
  });

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!sending) onOpenChange(next); }}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Contact admin</DialogTitle>
          <DialogDescription>
            Facing an issue with the application? Describe it below and it goes straight to support.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} className="space-y-4" noValidate>
          <FormField id="subject" label="Subject" error={errors.subject?.message} required>
            <Input id="subject" autoFocus {...register('subject')} />
          </FormField>
          <FormField id="message" label="What's going wrong?" error={errors.message?.message} required>
            <textarea
              id="message"
              rows={5}
              className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              {...register('message')}
            />
          </FormField>

          {/* CR-073. Optional, and labelled as such - a screenshot makes a
              report far easier to act on, but demanding one would turn a
              30-second report into a chore and fewer would get filed. */}
          <div className="space-y-2">
            <span className="text-sm font-medium">
              Screenshot <span className="font-normal text-muted-foreground">(optional)</span>
            </span>
            <input
              ref={fileInput}
              type="file"
              accept={SCREENSHOT_TYPES.join(',')}
              className="sr-only"
              onChange={(e) => chooseScreenshot(e.target.files?.[0])}
            />

            {previewUrl && screenshot ? (
              <div className="flex items-start gap-3 rounded-md border border-input p-2">
                <img
                  src={previewUrl}
                  alt={`Screenshot to attach: ${screenshot.name}`}
                  className="h-16 w-16 shrink-0 rounded object-cover"
                />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{screenshot.name}</p>
                  <p className="text-xs text-muted-foreground">
                    {Math.max(1, Math.round(screenshot.size / 1024))} KB
                  </p>
                </div>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  onClick={clearScreenshot}
                  disabled={sending}
                  aria-label="Remove screenshot"
                >
                  <X className="h-4 w-4" />
                </Button>
              </div>
            ) : (
              <Button
                type="button"
                variant="outline"
                className="w-full"
                onClick={() => fileInput.current?.click()}
                disabled={sending}
              >
                <ImagePlus className="h-4 w-4" />
                Attach a screenshot
              </Button>
            )}

            {screenshotError ? (
              <p className="text-sm text-destructive" role="alert">{screenshotError}</p>
            ) : (
              <p className="text-xs text-muted-foreground">PNG, JPEG or WebP, up to 2MB.</p>
            )}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={closeAndReset} disabled={sending}>
              Cancel
            </Button>
            <Button type="submit" loading={sending}>Send</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
