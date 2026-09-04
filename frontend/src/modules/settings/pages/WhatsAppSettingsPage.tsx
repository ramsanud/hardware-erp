import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { CheckCircle2, Loader2, MessageCircle, Unplug, XCircle } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Badge } from '@/shared/components/ui/badge';
import {
  Card, CardContent, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/shared/components/ui/dialog';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { FormField } from '@/shared/components/FormField';
import { PageHeader } from '@/shared/components/PageHeader';
import { BackLink } from '@/shared/components/BackLink';
import { ApiError } from '@/shared/types/api';
import { useToast } from '@/modules/auth/hooks/useToast';
import { SETTINGS_ROUTES } from '../constants';
import { whatsAppConnectionService } from '../services/whatsAppConnectionService';
import type { WhatsAppConnectionResponse } from '../types';

const connectSchema = z.object({
  businessAccountId: z.string().trim().min(1, 'WhatsApp Business Account ID is required').max(50),
  phoneNumberId: z.string().trim().min(1, 'Phone number ID is required').max(50),
  accessToken: z.string().trim().min(1, 'Access token is required').max(2000),
});
type ConnectFormValues = z.infer<typeof connectSchema>;

const testSendSchema = z.object({
  toMobileNo: z.string().trim().regex(/^[6-9][0-9]{9}$/, 'Enter a valid 10-digit mobile number'),
});
type TestSendFormValues = z.infer<typeof testSendSchema>;

/**
 * CR-056 - every hardware-shop tenant connects THEIR OWN WhatsApp Business
 * phone number here; there is no shared Hardware ERP number. Phase 1 is
 * manual credential entry (the owner pastes a token/phone-number-id/WABA-id
 * they already obtained from their own Meta Business Manager) rather than
 * Meta's Embedded Signup OAuth popup, which needs a Meta Tech Provider app
 * with Business verification and App Review already granted - not
 * something available to build from here. connect() calls the backend,
 * which itself makes a live Graph API call to verify these before saving
 * anything, so a typo'd token or phone number id is caught immediately.
 */
export function WhatsAppSettingsPage() {
  const toast = useToast();
  const [status, setStatus] = useState<WhatsAppConnectionResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [connectOpen, setConnectOpen] = useState(false);
  const [connectError, setConnectError] = useState<string | null>(null);
  const [disconnecting, setDisconnecting] = useState(false);
  const [testOpen, setTestOpen] = useState(false);
  const [testError, setTestError] = useState<string | null>(null);
  const [testResult, setTestResult] = useState<string | null>(null);

  const connectForm = useForm<ConnectFormValues>({
    resolver: zodResolver(connectSchema),
    defaultValues: { businessAccountId: '', phoneNumberId: '', accessToken: '' },
  });
  const testForm = useForm<TestSendFormValues>({
    resolver: zodResolver(testSendSchema),
    defaultValues: { toMobileNo: '' },
  });

  const reload = () => {
    setLoading(true);
    whatsAppConnectionService.getStatus()
      .then(setStatus)
      .catch(() => setStatus({ connected: false, status: 'DISCONNECTED' }))
      .finally(() => setLoading(false));
  };

  useEffect(() => { reload(); }, []);

  const submitConnect = connectForm.handleSubmit(async (values) => {
    setConnectError(null);
    try {
      const updated = await whatsAppConnectionService.connect(values);
      setStatus(updated);
      setConnectOpen(false);
      connectForm.reset({ businessAccountId: '', phoneNumberId: '', accessToken: '' });
      toast.success('WhatsApp Business connected.');
    } catch (caught) {
      setConnectError(caught instanceof ApiError ? caught.message : 'Could not connect WhatsApp. Please try again.');
    }
  });

  const handleDisconnect = async () => {
    setDisconnecting(true);
    try {
      const updated = await whatsAppConnectionService.disconnect();
      setStatus(updated);
      toast.success('WhatsApp Business disconnected.');
    } catch (caught) {
      toast.error(caught, 'Could not disconnect WhatsApp.');
    } finally {
      setDisconnecting(false);
    }
  };

  const submitTestSend = testForm.handleSubmit(async (values) => {
    setTestError(null);
    setTestResult(null);
    try {
      const result = await whatsAppConnectionService.testSend(values.toMobileNo);
      if (result === 'SENT') {
        setTestResult('Test message sent.');
      } else if (result === 'LOGGED_ONLY') {
        setTestResult('Logged only - not actually sent.');
      } else {
        setTestError('Message could not be sent. Please try again.');
      }
    } catch (caught) {
      setTestError(caught instanceof ApiError ? caught.message : 'Could not send the test message.');
    }
  });

  return (
    <>
      <BackLink to={SETTINGS_ROUTES.shop} label="Shop settings" />
      <PageHeader
        title="WhatsApp Business"
        description="Connect your own WhatsApp Business account to send invoices and payment reminders directly to your customers."
      />

      <div className="max-w-2xl space-y-5">
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <MessageCircle className="h-4 w-4 text-primary" aria-hidden />
              <CardTitle className="text-base">Connection status</CardTitle>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            {loading ? (
              <div className="flex justify-center py-6">
                <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" aria-label="Loading" />
              </div>
            ) : status?.connected ? (
              <div className="space-y-3">
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="h-5 w-5 text-success" aria-hidden />
                  <span className="font-medium">Connected</span>
                  {status.status === 'NEEDS_ATTENTION' ? (
                    <Badge variant="warning">Needs attention</Badge>
                  ) : null}
                </div>
                {status.status === 'NEEDS_ATTENTION' ? (
                  <Alert variant="destructive">
                    <AlertDescription>
                      WhatsApp connection needs attention - Meta rejected the last send attempt using this
                      token. Reconnect below with a fresh access token.
                    </AlertDescription>
                  </Alert>
                ) : null}
                <div className="grid gap-2 text-sm sm:grid-cols-2">
                  <div><p className="text-muted-foreground">Business</p><p>{status.businessName}</p></div>
                  <div><p className="text-muted-foreground">Phone</p><p className="tabular">{status.phoneNumberMasked}</p></div>
                </div>
                <div className="flex flex-wrap gap-2 pt-2">
                  <Button variant="outline" onClick={() => setTestOpen(true)}>Test WhatsApp</Button>
                  <Button variant="outline" asChild>
                    <Link to={SETTINGS_ROUTES.whatsappHistory}>Message History</Link>
                  </Button>
                  <Button variant="outline" onClick={() => setConnectOpen(true)}>Reconnect</Button>
                  <Button variant="outline" className="text-destructive hover:text-destructive"
                          onClick={handleDisconnect} loading={disconnecting}>
                    <Unplug className="h-4 w-4" />
                    Disconnect
                  </Button>
                </div>
              </div>
            ) : (
              <div className="space-y-3">
                <div className="flex items-center gap-2">
                  <XCircle className="h-5 w-5 text-muted-foreground" aria-hidden />
                  <span className="font-medium">Not connected</span>
                </div>
                <Button onClick={() => setConnectOpen(true)}>Connect WhatsApp Business</Button>
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle className="text-base">How to get these details</CardTitle></CardHeader>
          <CardContent className="space-y-2 text-sm text-muted-foreground">
            <p>
              These come from your own Meta Business Manager / WhatsApp Business Platform account -
              not from Hardware ERP. Open your app in Meta&apos;s developer console, under WhatsApp &gt;
              API Setup, and copy the Phone number ID, WhatsApp Business Account ID, and a permanent
              access token.
            </p>
            <p>
              Each shop connects its own number - your customers receive messages from your shop&apos;s
              own WhatsApp identity, never a shared Hardware ERP number.
            </p>
          </CardContent>
        </Card>
      </div>

      <Dialog open={connectOpen} onOpenChange={setConnectOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader><DialogTitle>Connect WhatsApp Business</DialogTitle></DialogHeader>
          <form onSubmit={submitConnect} noValidate className="space-y-4">
            {connectError ? <Alert variant="destructive"><AlertDescription>{connectError}</AlertDescription></Alert> : null}
            <FormField id="businessAccountId" label="WhatsApp Business Account ID"
                       error={connectForm.formState.errors.businessAccountId?.message} required>
              <Input id="businessAccountId" {...connectForm.register('businessAccountId')} />
            </FormField>
            <FormField id="phoneNumberId" label="Phone number ID"
                       error={connectForm.formState.errors.phoneNumberId?.message} required>
              <Input id="phoneNumberId" {...connectForm.register('phoneNumberId')} />
            </FormField>
            <FormField id="accessToken" label="Access token"
                       error={connectForm.formState.errors.accessToken?.message} required
                       hint="Kept encrypted on our server - never shown again after saving.">
              <Input id="accessToken" type="password" {...connectForm.register('accessToken')} />
            </FormField>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setConnectOpen(false)}
                      disabled={connectForm.formState.isSubmitting}>
                Cancel
              </Button>
              <Button type="submit" loading={connectForm.formState.isSubmitting}>Verify &amp; Connect</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={testOpen} onOpenChange={(open) => { setTestOpen(open); if (!open) { setTestError(null); setTestResult(null); } }}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader><DialogTitle>Test WhatsApp</DialogTitle></DialogHeader>
          <form onSubmit={submitTestSend} noValidate className="space-y-4">
            {testError ? <Alert variant="destructive"><AlertDescription>{testError}</AlertDescription></Alert> : null}
            {testResult ? <Alert><AlertDescription>{testResult}</AlertDescription></Alert> : null}
            <FormField id="toMobileNo" label="Send a test message to"
                       error={testForm.formState.errors.toMobileNo?.message} required>
              <Input id="toMobileNo" inputMode="tel" placeholder="9876543210" {...testForm.register('toMobileNo')} />
            </FormField>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setTestOpen(false)}
                      disabled={testForm.formState.isSubmitting}>
                Close
              </Button>
              <Button type="submit" loading={testForm.formState.isSubmitting}>Send</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
