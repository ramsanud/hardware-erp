import { useState, type FormEvent } from 'react';
import { Mail, MessageSquareText, Send } from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Label } from '@/shared/components/ui/label';
import {
  Card, CardContent, CardDescription, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import { useToast } from '@/modules/auth/hooks/useToast';
import {
  messagingDiagnosticService, type DiagnosticStatus,
} from '../services/messagingDiagnosticService';

/**
 * CR-085. One place to prove each outgoing channel before a customer depends
 * on it. POST /v1/settings/mail/test has existed since CR-038 but nothing on
 * screen ever called it, and SMS had no test at all - so a rejected Gmail
 * password or an unregistered Twilio sender stayed invisible until someone
 * did not receive a message. WhatsApp keeps its own test on its own page.
 */

interface Verdict {
  status: DiagnosticStatus;
  detail: string;
}

const TONE: Record<DiagnosticStatus, 'success' | 'warning' | 'destructive'> = {
  SENT: 'success',
  DELIVERED: 'success',
  READ: 'success',
  LOGGED_ONLY: 'warning',
  FAILED: 'destructive',
};

const LABEL: Record<DiagnosticStatus, string> = {
  SENT: 'Sent',
  DELIVERED: 'Delivered',
  READ: 'Read',
  LOGGED_ONLY: 'Not configured',
  FAILED: 'Failed',
};

function VerdictLine({ verdict, testId }: { verdict: Verdict | null; testId: string }) {
  if (!verdict) return null;
  return (
    <p className="flex items-start gap-2 text-sm" data-testid={testId}>
      <Badge variant={TONE[verdict.status]} className="shrink-0">{LABEL[verdict.status]}</Badge>
      {/* The provider's own words - "535-5.7.8 Username and Password not
          accepted", "21608 - unverified trial number" - are the part that
          says what to fix, so they are shown verbatim, never paraphrased. */}
      <span className="text-muted-foreground">{verdict.detail}</span>
    </p>
  );
}

export function MessagingDiagnosticsCard() {
  const toast = useToast();
  const [email, setEmail] = useState('');
  const [mobile, setMobile] = useState('');
  const [emailBusy, setEmailBusy] = useState(false);
  const [smsBusy, setSmsBusy] = useState(false);
  const [emailVerdict, setEmailVerdict] = useState<Verdict | null>(null);
  const [smsVerdict, setSmsVerdict] = useState<Verdict | null>(null);

  const sendEmail = async (event: FormEvent) => {
    event.preventDefault();
    setEmailBusy(true);
    try {
      const result = await messagingDiagnosticService.testEmail(email.trim());
      setEmailVerdict({ status: result.status, detail: result.detail });
    } catch (caught) {
      toast.error(caught, 'Could not run the email test.');
    } finally {
      setEmailBusy(false);
    }
  };

  const sendSms = async (event: FormEvent) => {
    event.preventDefault();
    setSmsBusy(true);
    try {
      const result = await messagingDiagnosticService.testSms(mobile.trim());
      setSmsVerdict({ status: result.status, detail: result.detail });
    } catch (caught) {
      toast.error(caught, 'Could not run the SMS test.');
    } finally {
      setSmsBusy(false);
    }
  };

  return (
    <Card data-testid="messaging-diagnostics">
      <CardHeader>
        <div className="flex items-center gap-2">
          <Send className="h-4 w-4 text-primary" aria-hidden />
          <CardTitle className="text-base">Test outgoing messages</CardTitle>
        </div>
        <CardDescription>
          Send yourself one email and one SMS to prove each channel works before a customer
          depends on it. The result is the provider&apos;s own answer.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        <form onSubmit={sendEmail} className="space-y-2">
          <Label htmlFor="diagnosticEmail" className="flex items-center gap-1.5">
            <Mail className="h-3.5 w-3.5" aria-hidden /> Email
          </Label>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Input id="diagnosticEmail" type="email" required inputMode="email" autoComplete="email"
                   placeholder="you@example.in" value={email} onChange={(e) => setEmail(e.target.value)} />
            <Button type="submit" variant="outline" loading={emailBusy} className="sm:w-40">Send test email</Button>
          </div>
          <VerdictLine verdict={emailVerdict} testId="email-verdict" />
        </form>

        <form onSubmit={sendSms} className="space-y-2">
          <Label htmlFor="diagnosticMobile" className="flex items-center gap-1.5">
            <MessageSquareText className="h-3.5 w-3.5" aria-hidden /> SMS
          </Label>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Input id="diagnosticMobile" inputMode="numeric" required pattern="[6-9][0-9]{9}" maxLength={10}
                   placeholder="10-digit mobile number" value={mobile}
                   onChange={(e) => setMobile(e.target.value.replace(/\D/g, ''))} />
            <Button type="submit" variant="outline" loading={smsBusy} className="sm:w-40">Send test SMS</Button>
          </div>
          <VerdictLine verdict={smsVerdict} testId="sms-verdict" />
        </form>
      </CardContent>
    </Card>
  );
}
