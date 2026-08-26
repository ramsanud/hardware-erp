import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter,
} from '@/shared/components/ui/dialog';
import { FormField } from '@/shared/components/FormField';
import { useToast } from '@/modules/auth/hooks/useToast';
import { notificationService } from '../services/notificationService';

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
  const {
    register, handleSubmit, reset, formState: { errors },
  } = useForm<ContactValues>({
    resolver: zodResolver(contactSchema),
    defaultValues: { subject: '', message: '' },
  });

  const submit = handleSubmit(async (values) => {
    setSending(true);
    try {
      await notificationService.contactAdmin(values);
      toast.success("Your message was sent. We'll get back to you soon.");
      reset();
      onOpenChange(false);
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
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={sending}>
              Cancel
            </Button>
            <Button type="submit" loading={sending}>Send</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
