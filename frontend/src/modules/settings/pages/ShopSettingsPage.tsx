import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Check, FileText, Loader2, MessageCircle, Palette, Pencil, QrCode, Save, Sparkles, Store } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import {
  Card, CardContent, CardHeader, CardTitle, CardDescription,
} from '@/shared/components/ui/card';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import { Badge } from '@/shared/components/ui/badge';
import { Checkbox } from '@/shared/components/ui/checkbox';
import { PageHeader } from '@/shared/components/PageHeader';
import { ErrorState } from '@/shared/components/ErrorState';
import { FormField } from '@/shared/components/FormField';
import { ImageUpload } from '@/shared/components/ImageUpload';
import { SignaturePad } from '@/shared/components/SignaturePad';
import { UnsavedChangesDialog } from '@/shared/components/UnsavedChangesDialog';
import { useAuthenticatedImage } from '@/shared/hooks/useAuthenticatedImage';
import { useDeploymentConfig } from '@/shared/hooks/useDeploymentConfig';
import { cn } from '@/shared/lib/utils';
import { INDIAN_STATES } from '@/shared/data/indianStates';
import { BANK_OTHER, INDIAN_BANKS } from '@/shared/data/indianBanks';
import { ApiError } from '@/shared/types/api';
import { useToast } from '@/modules/auth/hooks/useToast';
import { AUTH_ROUTES } from '@/modules/auth/constants';
import { useAppChrome } from '@/layouts/AppChromeProvider';
import { settingsService } from '../services/settingsService';
import { SETTINGS_ROUTES } from '../constants';
import { SUBSCRIPTION_TIER_OPTIONS, SUBSCRIPTION_TIERS } from '../constants/subscriptionTiers';
import { INVOICE_THEME_OPTIONS, INVOICE_THEMES } from '../constants/invoiceThemes';
import { SubscriptionCouponsCard } from '../components/SubscriptionCouponsCard';
import { BillingUpgradeCard } from '../components/BillingUpgradeCard';
import { BankAccountsCard } from '../components/BankAccountsCard';
import type { InvoiceTheme, SubscriptionTier, TenantSettingsResponse, UsageSummaryResponse } from '../types';

const SETTINGS_FORM_ID = 'shop-settings-form';

const settingsSchema = z.object({
  name: z.string().trim().min(1, 'Shop name is required').max(200),
  gstNo: z.string().trim().toUpperCase()
    .regex(/^\d{2}[A-Z]{5}\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/, 'Enter a valid 15-character GSTIN')
    .optional().or(z.literal('')),
  addressLine1: z.string().trim().max(255).optional().or(z.literal('')),
  addressLine2: z.string().trim().max(255).optional().or(z.literal('')),
  city: z.string().trim().max(100).optional().or(z.literal('')),
  stateCode: z.string().trim().regex(/^\d{2}$/, 'State code is 2 digits').optional().or(z.literal('')),
  pincode: z.string().trim().regex(/^\d{6}$/, 'Enter a valid 6-digit pincode').optional().or(z.literal('')),
  signatoryName: z.string().trim().max(100).optional().or(z.literal('')),
  panNo: z.string().trim().toUpperCase()
    .regex(/^[A-Z]{5}\d{4}[A-Z]$/, 'Enter a valid 10-character PAN').optional().or(z.literal('')),
  phone: z.string().trim().max(15).optional().or(z.literal('')),
  email: z.string().trim().email('Enter a valid email address').optional().or(z.literal('')),
  bankAccountName: z.string().trim().max(200).optional().or(z.literal('')),
  bankAccountNo: z.string().trim().regex(/^$|^[0-9]{9,18}$/, 'Account number must be 9-18 digits')
    .optional().or(z.literal('')),
  bankIfsc: z.string().trim().toUpperCase()
    .regex(/^$|^[A-Z]{4}0[A-Z0-9]{6}$/, 'Enter a valid IFSC code').optional().or(z.literal('')),
  bankName: z.string().trim().max(200).optional().or(z.literal('')),
  upiId: z.string().trim().regex(/^$|^[\w.\-]{2,256}@[a-zA-Z][\w]{2,64}$/, 'Enter a valid UPI ID, e.g. shopname@okicici')
    .optional().or(z.literal('')),
});
type SettingsFormValues = z.infer<typeof settingsSchema>;

function toFormValues(settings: TenantSettingsResponse): SettingsFormValues {
  return {
    name: settings.name,
    gstNo: settings.gstNo ?? '',
    addressLine1: settings.addressLine1 ?? '',
    addressLine2: settings.addressLine2 ?? '',
    city: settings.city ?? '',
    stateCode: settings.stateCode ?? '',
    pincode: settings.pincode ?? '',
    signatoryName: settings.signatoryName ?? '',
    panNo: settings.panNo ?? '',
    phone: settings.phone ?? '',
    email: settings.email ?? '',
    bankAccountName: settings.bankAccountName ?? '',
    bankAccountNo: settings.bankAccountNo ?? '',
    bankIfsc: settings.bankIfsc ?? '',
    bankName: settings.bankName ?? '',
    upiId: settings.upiId ?? '',
  };
}

export function ShopSettingsPage() {
  const toast = useToast();
  const { refreshBrand } = useAppChrome();
  // CR-059. A self-hosted installation has no subscription to buy, so the
  // purchase paths are hidden rather than left to fail - the server refuses
  // them with BILLING_NOT_APPLICABLE, and a button that always errors is
  // worse than no button. `resolved` keeps the hosted-default fallback from
  // flashing an upgrade prompt at a client who already owns the software.
  const deployment = useDeploymentConfig();
  const showBilling = deployment.resolved && deployment.billingEnabled;
  const [settings, setSettings] = useState<TenantSettingsResponse | null>(null);
  const [usage, setUsage] = useState<UsageSummaryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  const [editMode, setEditMode] = useState(false);
  const [confirmingClose, setConfirmingClose] = useState(false);

  const [logoVersion, setLogoVersion] = useState(0);
  const [signatureVersion, setSignatureVersion] = useState(0);
  const [upiQrVersion, setUpiQrVersion] = useState(0);

  /**
   * The fields this form actually renders. A server error naming anything
   * else must not be silently swallowed by setError on a field that does not
   * exist - it goes to the toast instead.
   */
  const FORM_FIELDS: Record<keyof SettingsFormValues, true> = {
    name: true, gstNo: true, addressLine1: true, addressLine2: true, city: true,
    stateCode: true, pincode: true, signatoryName: true, panNo: true, phone: true,
    email: true, bankAccountName: true, bankAccountNo: true, bankIfsc: true,
    bankName: true, upiId: true,
  };

  const {
    register, handleSubmit, reset, watch, setValue, setError: setFieldError, formState: { errors, isSubmitting, isDirty },
  } = useForm<SettingsFormValues>({
    resolver: zodResolver(settingsSchema),
    defaultValues: {
      name: '', gstNo: '', addressLine1: '', addressLine2: '', city: '', stateCode: '', pincode: '', signatoryName: '',
      panNo: '', phone: '', email: '',
      bankAccountName: '', bankAccountNo: '', bankIfsc: '', bankName: '', upiId: '',
    },
  });
  const values = watch();

  const logoSrc = useAuthenticatedImage(settings?.hasLogo ? settingsService.logoUrl : null, logoVersion);
  const signatureSrc = useAuthenticatedImage(
    settings?.hasSignatureImage ? settingsService.signatureUrl : null, signatureVersion);
  const upiQrSrc = useAuthenticatedImage(
    settings?.hasUpiQrImage ? settingsService.upiQrUrl : null, upiQrVersion);

  const reload = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await settingsService.get();
      setSettings(data);
      reset(toFormValues(data));
      // Usage is informational only - a failure here must never block the
      // settings page itself from loading, so it's fetched separately and
      // silently left null on error rather than surfacing a second ErrorState.
      settingsService.usage().then(setUsage).catch(() => setUsage(null));
    } catch (caught) {
      setError(caught instanceof ApiError
        ? caught
        : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 }));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void reload(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const submit = handleSubmit(async (values) => {
    // The form only renders once `settings` has loaded (see the early
    // return above), but that guarantee is not visible to TypeScript across
    // this closure - the six Additional Settings fields below need a
    // narrowed `settings` to read their current, unedited values from.
    if (!settings) return;
    try {
      const updated = await settingsService.update({
        name: values.name,
        gstNo: values.gstNo || null,
        addressLine1: values.addressLine1 || null,
        addressLine2: values.addressLine2 || null,
        city: values.city || null,
        stateCode: values.stateCode || null,
        pincode: values.pincode || null,
        signatoryName: values.signatoryName || null,
        panNo: values.panNo || null,
        phone: values.phone || null,
        email: values.email || null,
        bankAccountName: values.bankAccountName || null,
        bankAccountNo: values.bankAccountNo || null,
        bankIfsc: values.bankIfsc || null,
        bankName: values.bankName || null,
        upiId: values.upiId || null,
        showItemDescription: settings.showItemDescription,
        showAlternateUnit: settings.showAlternateUnit,
        showPriceHistory: settings.showPriceHistory,
        enableFreeQuantity: settings.enableFreeQuantity,
        showInvoiceTime: settings.showInvoiceTime,
        showItemImage: settings.showItemImage,
        invoiceTagline: settings.invoiceTagline,
        tdsEnabled: settings.tdsEnabled,
        tdsSectionCode: settings.tdsSectionCode,
        tdsRatePercent: settings.tdsRatePercent,
        tcsEnabled: settings.tcsEnabled,
        tcsSectionCode: settings.tcsSectionCode,
        tcsRatePercent: settings.tcsRatePercent,
        einvoiceEnabled: settings.einvoiceEnabled,
        paymentDueReminderEnabled: settings.paymentDueReminderEnabled,
        lowStockAlertEnabled: settings.lowStockAlertEnabled,
      });
      setSettings(updated);
      reset(toFormValues(updated));
      await refreshBrand();
      setConfirmingClose(false);
      setEditMode(false);
      toast.success('Shop settings saved.');
    } catch (caught) {
      // BUG-SET-001: the server names the offending field in errors, but the
      // page only showed the generic message - so "Please correct the
      // highlighted fields" appeared with nothing highlighted, and the owner
      // had no way to know which of sixteen inputs was wrong.
      //
      // Field errors are mapped onto the form so the input itself reports the
      // problem; anything the form has no input for still reaches the toast,
      // rather than being swallowed.
      const unmapped: string[] = [];
      if (caught instanceof ApiError && caught.fieldErrors) {
        for (const [field, message] of Object.entries(caught.fieldErrors)) {
          if (field in FORM_FIELDS) {
            setFieldError(field as keyof SettingsFormValues, { type: 'server', message });
          } else {
            unmapped.push(`${field}: ${message}`);
          }
        }
      }
      toast.error(caught, unmapped.length > 0
        ? `Could not save shop settings. ${unmapped.join('; ')}`
        : 'Could not save shop settings. Check the highlighted fields.');
    }
  });

  const requestExitEditMode = () => {
    if (isDirty) { setConfirmingClose(true); return; }
    setEditMode(false);
  };

  // Independent of the main edit form - built from the last-saved `settings`,
  // never the in-progress form values, so switching plans never accidentally
  // saves an unrelated unfinished edit.
  const changeTier = async (tier: SubscriptionTier) => {
    if (!settings) return;
    try {
      const updated = await settingsService.update({
        name: settings.name,
        gstNo: settings.gstNo,
        addressLine1: settings.addressLine1,
        addressLine2: settings.addressLine2,
        city: settings.city,
        stateCode: settings.stateCode,
        pincode: settings.pincode,
        signatoryName: settings.signatoryName,
        panNo: settings.panNo,
        phone: settings.phone,
        email: settings.email,
        bankAccountName: settings.bankAccountName,
        bankAccountNo: settings.bankAccountNo,
        bankIfsc: settings.bankIfsc,
        bankName: settings.bankName,
        upiId: settings.upiId,
        subscriptionTier: tier,
        showItemDescription: settings.showItemDescription,
        showAlternateUnit: settings.showAlternateUnit,
        showPriceHistory: settings.showPriceHistory,
        enableFreeQuantity: settings.enableFreeQuantity,
        showInvoiceTime: settings.showInvoiceTime,
        showItemImage: settings.showItemImage,
        invoiceTagline: settings.invoiceTagline,
        tdsEnabled: settings.tdsEnabled,
        tdsSectionCode: settings.tdsSectionCode,
        tdsRatePercent: settings.tdsRatePercent,
        tcsEnabled: settings.tcsEnabled,
        tcsSectionCode: settings.tcsSectionCode,
        tcsRatePercent: settings.tcsRatePercent,
        einvoiceEnabled: settings.einvoiceEnabled,
        paymentDueReminderEnabled: settings.paymentDueReminderEnabled,
        lowStockAlertEnabled: settings.lowStockAlertEnabled,
      });
      setSettings(updated);
      settingsService.usage().then(setUsage).catch(() => setUsage(null));
      await refreshBrand();
      toast.success(`Plan changed to ${SUBSCRIPTION_TIERS[tier].label}.`);
    } catch (caught) {
      toast.error(caught, 'Could not change the plan.');
    }
  };

  // Same "own instant-apply Select, outside the main form" shape as
  // changeTier() above - invoiceTheme is shop data (server-side, printed on
  // every generated PDF), not the per-person Appearance preference the
  // AppearanceCard below links out to, so it belongs here rather than there.
  const changeInvoiceTheme = async (theme: InvoiceTheme) => {
    if (!settings) return;
    try {
      const updated = await settingsService.update({
        name: settings.name,
        gstNo: settings.gstNo,
        addressLine1: settings.addressLine1,
        addressLine2: settings.addressLine2,
        city: settings.city,
        stateCode: settings.stateCode,
        pincode: settings.pincode,
        signatoryName: settings.signatoryName,
        panNo: settings.panNo,
        phone: settings.phone,
        email: settings.email,
        bankAccountName: settings.bankAccountName,
        bankAccountNo: settings.bankAccountNo,
        bankIfsc: settings.bankIfsc,
        bankName: settings.bankName,
        upiId: settings.upiId,
        invoiceTheme: theme,
        showItemDescription: settings.showItemDescription,
        showAlternateUnit: settings.showAlternateUnit,
        showPriceHistory: settings.showPriceHistory,
        enableFreeQuantity: settings.enableFreeQuantity,
        showInvoiceTime: settings.showInvoiceTime,
        showItemImage: settings.showItemImage,
        invoiceTagline: settings.invoiceTagline,
        tdsEnabled: settings.tdsEnabled,
        tdsSectionCode: settings.tdsSectionCode,
        tdsRatePercent: settings.tdsRatePercent,
        tcsEnabled: settings.tcsEnabled,
        tcsSectionCode: settings.tcsSectionCode,
        tcsRatePercent: settings.tcsRatePercent,
        einvoiceEnabled: settings.einvoiceEnabled,
        paymentDueReminderEnabled: settings.paymentDueReminderEnabled,
        lowStockAlertEnabled: settings.lowStockAlertEnabled,
      });
      setSettings(updated);
      toast.success(`Invoice theme changed to ${INVOICE_THEMES[theme].label}.`);
    } catch (caught) {
      toast.error(caught, 'Could not change the invoice theme.');
    }
  };

  /**
   * CR-053 backlog item 1. Same "own instant-apply action, outside the main
   * form" shape as changeTier/changeInvoiceTheme above - takes a partial
   * patch of just the Additional Settings fields and layers it over the
   * last-saved settings, so toggling one checkbox never touches anything
   * else on the form (including an in-progress unsaved edit elsewhere,
   * since this reads from `settings`, not the form's live values).
   */
  const changeAdditionalSettings = async (
    patch: Partial<Pick<TenantSettingsResponse,
      'showItemDescription' | 'showAlternateUnit' | 'showPriceHistory' | 'enableFreeQuantity'
      | 'showInvoiceTime' | 'showItemImage' | 'invoiceTagline' | 'einvoiceEnabled'
      | 'paymentDueReminderEnabled' | 'lowStockAlertEnabled'>>,
  ) => {
    if (!settings) return;
    try {
      const updated = await settingsService.update({
        name: settings.name,
        gstNo: settings.gstNo,
        addressLine1: settings.addressLine1,
        addressLine2: settings.addressLine2,
        city: settings.city,
        stateCode: settings.stateCode,
        pincode: settings.pincode,
        signatoryName: settings.signatoryName,
        panNo: settings.panNo,
        phone: settings.phone,
        email: settings.email,
        bankAccountName: settings.bankAccountName,
        bankAccountNo: settings.bankAccountNo,
        bankIfsc: settings.bankIfsc,
        bankName: settings.bankName,
        upiId: settings.upiId,
        showItemDescription: settings.showItemDescription,
        showAlternateUnit: settings.showAlternateUnit,
        showPriceHistory: settings.showPriceHistory,
        enableFreeQuantity: settings.enableFreeQuantity,
        showInvoiceTime: settings.showInvoiceTime,
        showItemImage: settings.showItemImage,
        invoiceTagline: settings.invoiceTagline,
        tdsEnabled: settings.tdsEnabled,
        tdsSectionCode: settings.tdsSectionCode,
        tdsRatePercent: settings.tdsRatePercent,
        tcsEnabled: settings.tcsEnabled,
        tcsSectionCode: settings.tcsSectionCode,
        tcsRatePercent: settings.tcsRatePercent,
        einvoiceEnabled: settings.einvoiceEnabled,
        paymentDueReminderEnabled: settings.paymentDueReminderEnabled,
        lowStockAlertEnabled: settings.lowStockAlertEnabled,
        ...patch,
      });
      setSettings(updated);
      // showPriceHistory/enableFreeQuantity are also cached in AppChromeProvider
      // (ProductDetailPage and InvoiceWizard read them from there, not from a
      // fresh fetch of their own) - without this, a toggle flipped here would
      // not take effect anywhere else until a full page reload.
      void refreshBrand();
      toast.success('Additional settings saved.');
    } catch (caught) {
      toast.error(caught, 'Could not save additional settings.');
    }
  };

  /** CR-053 backlog item 3. Same instant-apply shape as changeAdditionalSettings above. */
  const changeTdsTcs = async (
    patch: Partial<Pick<TenantSettingsResponse,
      'tdsEnabled' | 'tdsSectionCode' | 'tdsRatePercent' | 'tcsEnabled' | 'tcsSectionCode' | 'tcsRatePercent'>>,
  ) => {
    if (!settings) return;
    try {
      const updated = await settingsService.update({
        name: settings.name,
        gstNo: settings.gstNo,
        addressLine1: settings.addressLine1,
        addressLine2: settings.addressLine2,
        city: settings.city,
        stateCode: settings.stateCode,
        pincode: settings.pincode,
        signatoryName: settings.signatoryName,
        panNo: settings.panNo,
        phone: settings.phone,
        email: settings.email,
        bankAccountName: settings.bankAccountName,
        bankAccountNo: settings.bankAccountNo,
        bankIfsc: settings.bankIfsc,
        bankName: settings.bankName,
        upiId: settings.upiId,
        showItemDescription: settings.showItemDescription,
        showAlternateUnit: settings.showAlternateUnit,
        showPriceHistory: settings.showPriceHistory,
        enableFreeQuantity: settings.enableFreeQuantity,
        showInvoiceTime: settings.showInvoiceTime,
        showItemImage: settings.showItemImage,
        invoiceTagline: settings.invoiceTagline,
        tdsEnabled: settings.tdsEnabled,
        tdsSectionCode: settings.tdsSectionCode,
        tdsRatePercent: settings.tdsRatePercent,
        tcsEnabled: settings.tcsEnabled,
        tcsSectionCode: settings.tcsSectionCode,
        tcsRatePercent: settings.tcsRatePercent,
        einvoiceEnabled: settings.einvoiceEnabled,
        paymentDueReminderEnabled: settings.paymentDueReminderEnabled,
        lowStockAlertEnabled: settings.lowStockAlertEnabled,
        ...patch,
      });
      setSettings(updated);
      void refreshBrand();
      toast.success('TDS/TCS settings saved.');
    } catch (caught) {
      toast.error(caught, 'Could not save TDS/TCS settings.');
    }
  };

  /**
   * Patches subscriptionTier/subscriptionTrialExpiresAt directly rather
   * than calling reload() - reload() flips `loading`, which unmounts this
   * whole page (including SubscriptionCouponsCard) while it refetches,
   * wiping that card's own just-shown success message before it's ever
   * seen. Caught live while testing the redeem flow, not hypothetical.
   */
  const handleCouponRedeemed = (result: { grantedTier: SubscriptionTier; trialExpiresAt: string }) => {
    setSettings((current) => (current
      ? { ...current, subscriptionTier: result.grantedTier, subscriptionTrialExpiresAt: result.trialExpiresAt }
      : current));
    settingsService.usage().then(setUsage).catch(() => setUsage(null));
    void refreshBrand();
  };

  /** Same "patch in place, don't reload()" reasoning as handleCouponRedeemed above. */
  const handleUpgraded = (tier: SubscriptionTier) => {
    setSettings((current) => (current
      ? { ...current, subscriptionTier: tier, subscriptionTrialExpiresAt: null }
      : current));
    settingsService.usage().then(setUsage).catch(() => setUsage(null));
    void refreshBrand();
  };

  if (error) return <Card><ErrorState error={error} onRetry={reload} /></Card>;
  if (loading || !settings) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
      </div>
    );
  }

  if (!editMode) {
    return (
      <>
        <PageHeader
          title="Shop settings"
          description="Your GST details and signature appear on every invoice and quotation PDF."
          actions={
            <Button variant="outline" onClick={() => setEditMode(true)}>
              <Pencil className="h-4 w-4" />
              Edit
            </Button>
          }
        />

        <div className="max-w-2xl space-y-5">
          {showBilling ? <SubscriptionPlanCard currentTier={settings.subscriptionTier} trialExpiresAt={settings.subscriptionTrialExpiresAt} onChangeTier={changeTier} /> : null}
          {showBilling ? (
            <BillingUpgradeCard currentTier={settings.subscriptionTier} tenantName={settings.name}
                                 ownerEmail={settings.email} ownerPhone={settings.phone} onUpgraded={handleUpgraded} />
          ) : null}
          {usage ? <UsageCard usage={usage} /> : null}
          {showBilling ? <SubscriptionCouponsCard onRedeemed={handleCouponRedeemed} /> : null}
          <InvoiceThemeCard currentTheme={settings.invoiceTheme} onChangeTheme={changeInvoiceTheme} />
          <AdditionalSettingsCard settings={settings} onChange={changeAdditionalSettings} />
          <TdsTcsCard settings={settings} onChange={changeTdsTcs} />
          <WhatsAppBusinessCard />
          <AppearanceCard />

          <Card>
            <CardHeader><CardTitle className="text-base">Branding</CardTitle></CardHeader>
            <CardContent className="flex items-center gap-4">
              <span className="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-lg border bg-muted">
                {logoSrc ? (
                  <img src={logoSrc} alt={`${settings.name} logo`} className="h-full w-full object-cover" />
                ) : (
                  <Store className="h-7 w-7 text-muted-foreground" aria-hidden />
                )}
              </span>
              <p className="text-lg font-medium">{settings.name}</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle className="text-base">GST registration</CardTitle></CardHeader>
            <CardContent className="grid gap-3 text-sm sm:grid-cols-2">
              <div><p className="text-muted-foreground">GSTIN</p><p className="tabular">{settings.gstNo ?? '—'}</p></div>
              <div><p className="text-muted-foreground">State code</p><p className="tabular">{settings.stateCode ?? '—'}</p></div>
              <div><p className="text-muted-foreground">PAN</p><p className="tabular">{settings.panNo ?? '—'}</p></div>
              <div><p className="text-muted-foreground">Phone</p><p className="tabular">{settings.phone ?? '—'}</p></div>
              <div className="sm:col-span-2"><p className="text-muted-foreground">Email</p><p>{settings.email ?? '—'}</p></div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle className="text-base">Shop address</CardTitle></CardHeader>
            <CardContent className="text-sm">
              <p>{settings.addressLine1 || '—'}</p>
              {settings.addressLine2 ? <p>{settings.addressLine2}</p> : null}
              <p>{[settings.city, settings.pincode].filter(Boolean).join(' · ') || '—'}</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle className="text-base">Bank &amp; payment details</CardTitle></CardHeader>
            <CardContent className="grid gap-3 text-sm sm:grid-cols-2">
              <div><p className="text-muted-foreground">Account name</p><p>{settings.bankAccountName ?? '—'}</p></div>
              <div><p className="text-muted-foreground">Account number</p><p className="tabular">{settings.bankAccountNo ?? '—'}</p></div>
              <div><p className="text-muted-foreground">IFSC code</p><p className="tabular">{settings.bankIfsc ?? '—'}</p></div>
              <div><p className="text-muted-foreground">Bank name</p><p>{settings.bankName ?? '—'}</p></div>
              <div className="sm:col-span-2"><p className="text-muted-foreground">UPI ID</p><p>{settings.upiId ?? '—'}</p></div>
              <div className="sm:col-span-2">
                <p className="text-muted-foreground">UPI QR code</p>
                {upiQrSrc ? (
                  <img src={upiQrSrc} alt="UPI QR code" className="mt-1 h-28 w-28 rounded-lg border object-contain" />
                ) : (
                  <p>—</p>
                )}
              </div>
            </CardContent>
          </Card>

          <BankAccountsCard />

          <Card>
            <CardHeader><CardTitle className="text-base">Digital signature</CardTitle></CardHeader>
            <CardContent className="flex items-center gap-4">
              <span className="flex h-16 w-32 shrink-0 items-center justify-center overflow-hidden rounded-lg border bg-muted">
                {signatureSrc ? (
                  <img src={signatureSrc} alt="Signature" className="h-full w-full object-contain" />
                ) : (
                  <span className="text-xs text-muted-foreground">No signature</span>
                )}
              </span>
              <p className="text-sm">{settings.signatoryName || '—'}</p>
            </CardContent>
          </Card>
        </div>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Shop settings"
        description="Your GST details and signature appear on every invoice and quotation PDF."
      />

      <div className="max-w-2xl mb-5 space-y-5">
        {showBilling ? <SubscriptionPlanCard currentTier={settings.subscriptionTier} trialExpiresAt={settings.subscriptionTrialExpiresAt} onChangeTier={changeTier} /> : null}
        {showBilling ? (
          <BillingUpgradeCard currentTier={settings.subscriptionTier} tenantName={settings.name}
                               ownerEmail={settings.email} ownerPhone={settings.phone} onUpgraded={handleUpgraded} />
        ) : null}
        {usage ? <UsageCard usage={usage} /> : null}
        {showBilling ? <SubscriptionCouponsCard onRedeemed={handleCouponRedeemed} /> : null}
        <InvoiceThemeCard currentTheme={settings.invoiceTheme} onChangeTheme={changeInvoiceTheme} />
        <AdditionalSettingsCard settings={settings} onChange={changeAdditionalSettings} />
        <TdsTcsCard settings={settings} onChange={changeTdsTcs} />
        <WhatsAppBusinessCard />
      </div>

      <form id={SETTINGS_FORM_ID} onSubmit={submit} noValidate className="max-w-2xl space-y-5">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Branding</CardTitle>
            <CardDescription>Your shop name and logo appear in the sidebar and on every PDF.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            <ImageUpload
              src={logoSrc}
              alt={`${settings.name} logo`}
              shape="square"
              fallback={<Store className="h-8 w-8 text-muted-foreground" aria-hidden />}
              onUpload={async (file) => {
                await settingsService.uploadLogo(file);
                setLogoVersion((v) => v + 1);
                setSettings((current) => (current ? { ...current, hasLogo: true } : current));
                await refreshBrand();
              }}
              onRemove={async () => {
                await settingsService.removeLogo();
                setLogoVersion((v) => v + 1);
                setSettings((current) => (current ? { ...current, hasLogo: false } : current));
                await refreshBrand();
              }}
            />
            <FormField id="name" label="Shop name" error={errors.name?.message} required>
              <Input id="name" {...register('name')} />
            </FormField>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">GST registration</CardTitle>
            <CardDescription>
              Required for the shop's GSTIN to appear on tax invoices, and to correctly split
              CGST+SGST vs IGST against a customer's own state.
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <FormField id="gstNo" label="Shop GSTIN" error={errors.gstNo?.message} className="sm:col-span-2">
              <Input id="gstNo" maxLength={15} className="uppercase" {...register('gstNo')} />
            </FormField>
            <FormField id="stateCode" label="State code" error={errors.stateCode?.message}
                       hint="First 2 digits of the GSTIN, e.g. 29 for Karnataka.">
              <Input id="stateCode" inputMode="numeric" maxLength={2} placeholder="29" {...register('stateCode')} />
            </FormField>
            <FormField id="panNo" label="PAN" error={errors.panNo?.message}>
              <Input id="panNo" maxLength={10} className="uppercase" {...register('panNo')} />
            </FormField>
            <FormField id="phone" label="Phone" error={errors.phone?.message}>
              <Input id="phone" inputMode="tel" {...register('phone')} />
            </FormField>
            <FormField id="email" label="Email" error={errors.email?.message} className="sm:col-span-2">
              <Input id="email" type="email" {...register('email')} />
            </FormField>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Shop address</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <FormField id="addressLine1" label="Address line 1" error={errors.addressLine1?.message} className="sm:col-span-2">
              <Input id="addressLine1" {...register('addressLine1')} />
            </FormField>
            <FormField id="addressLine2" label="Address line 2" error={errors.addressLine2?.message} className="sm:col-span-2">
              <Input id="addressLine2" {...register('addressLine2')} />
            </FormField>
            <FormField id="city" label="City" error={errors.city?.message}>
              <Input id="city" {...register('city')} />
            </FormField>
            <FormField id="pincode" label="Pincode" error={errors.pincode?.message}>
              <Input id="pincode" inputMode="numeric" maxLength={6} {...register('pincode')} />
            </FormField>
            <FormField id="stateName" label="State" hint="Fills in the GST state code above automatically."
                       className="sm:col-span-2">
              <Select
                value={INDIAN_STATES.find((s) => s.code === values.stateCode)?.name ?? ''}
                onValueChange={(name) => {
                  const state = INDIAN_STATES.find((s) => s.name === name);
                  if (state) setValue('stateCode', state.code, { shouldDirty: true, shouldValidate: true });
                }}
              >
                <SelectTrigger id="stateName"><SelectValue placeholder="Select a state" /></SelectTrigger>
                <SelectContent>
                  {INDIAN_STATES.map((state) => (
                    <SelectItem key={state.code} value={state.name}>{state.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </FormField>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Bank &amp; payment details</CardTitle>
            <CardDescription>
              Printed on the invoice PDF's payment section. Upload your own GPay/PhonePe QR code
              below, or leave it blank and one is generated from the UPI ID.
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <FormField id="bankAccountName" label="Account name" error={errors.bankAccountName?.message}>
              <Input id="bankAccountName" {...register('bankAccountName')} />
            </FormField>
            <FormField id="bankAccountNo" label="Account number" error={errors.bankAccountNo?.message}>
              <Input id="bankAccountNo" inputMode="numeric" {...register('bankAccountNo')} />
            </FormField>
            <FormField id="bankIfsc" label="IFSC code" error={errors.bankIfsc?.message}>
              <Input id="bankIfsc" placeholder="HDFC0001234" className="uppercase" {...register('bankIfsc')} />
            </FormField>
            <FormField id="bankName" label="Bank name" error={errors.bankName?.message}>
              <Select
                value={INDIAN_BANKS.includes(values.bankName ?? '') ? values.bankName
                  : (values.bankName ? BANK_OTHER : '')}
                onValueChange={(bank) => setValue('bankName', bank === BANK_OTHER ? '' : bank,
                  { shouldDirty: true, shouldValidate: true })}
              >
                <SelectTrigger id="bankName"><SelectValue placeholder="Select a bank" /></SelectTrigger>
                <SelectContent>
                  {INDIAN_BANKS.map((bank) => <SelectItem key={bank} value={bank}>{bank}</SelectItem>)}
                  <SelectItem value={BANK_OTHER}>Other</SelectItem>
                </SelectContent>
              </Select>
              {!INDIAN_BANKS.includes(values.bankName ?? '') ? (
                <Input className="mt-2" placeholder="Enter bank name" {...register('bankName')} />
              ) : null}
            </FormField>
            <FormField id="upiId" label="UPI ID" error={errors.upiId?.message}
                       hint="e.g. shopname@okicici" className="sm:col-span-2">
              <Input id="upiId" {...register('upiId')} />
            </FormField>
            <FormField id="upiQr" label="UPI QR code (optional)" className="sm:col-span-2">
              <ImageUpload
                src={upiQrSrc}
                alt="UPI QR code"
                shape="square"
                fallback={<QrCode className="h-8 w-8 text-muted-foreground" aria-hidden />}
                onUpload={async (file) => {
                  await settingsService.uploadUpiQr(file);
                  setUpiQrVersion((v) => v + 1);
                  setSettings((current) => (current ? { ...current, hasUpiQrImage: true } : current));
                }}
                onRemove={async () => {
                  await settingsService.removeUpiQr();
                  setUpiQrVersion((v) => v + 1);
                  setSettings((current) => (current ? { ...current, hasUpiQrImage: false } : current));
                }}
              />
            </FormField>
          </CardContent>
        </Card>

        <BankAccountsCard />

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Digital signature</CardTitle>
            <CardDescription>
              Draw or upload a signature to print on invoice/quotation PDFs, alongside the signatory name below.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            <SignaturePad
              src={signatureSrc}
              onUpload={async (file) => {
                await settingsService.uploadSignature(file);
                setSignatureVersion((v) => v + 1);
                setSettings((current) => (current ? { ...current, hasSignatureImage: true } : current));
              }}
              onRemove={async () => {
                await settingsService.removeSignature();
                setSignatureVersion((v) => v + 1);
                setSettings((current) => (current ? { ...current, hasSignatureImage: false } : current));
              }}
            />
            <FormField id="signatoryName" label="Signatory name" error={errors.signatoryName?.message}
                       hint="Usually the shop owner or an authorized staff member.">
              <Input id="signatoryName" {...register('signatoryName')} />
            </FormField>
          </CardContent>
        </Card>

        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={requestExitEditMode} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button type="submit" loading={isSubmitting} disabled={!isDirty}>
            <Save className="h-4 w-4" />
            Save settings
          </Button>
        </div>
      </form>

      <UnsavedChangesDialog
        open={confirmingClose}
        onContinueEditing={() => setConfirmingClose(false)}
        onDiscard={() => { setConfirmingClose(false); reset(); setEditMode(false); }}
        formId={SETTINGS_FORM_ID}
        saving={isSubmitting}
      />
    </>
  );
}

/**
 * Self-declared feature-gating flag (CR-027) - downgrades always apply
 * instantly, no charge. Once real billing is configured (CR-057 phase 9),
 * an upgrade attempted here is rejected server-side with a message pointing
 * at BillingUpgradeCard's real Razorpay checkout instead; with no gateway
 * configured, this remains the only way to change plan, exactly as before.
 */
function SubscriptionPlanCard({
  currentTier, trialExpiresAt, onChangeTier,
}: {
  currentTier: SubscriptionTier;
  trialExpiresAt?: string | null;
  onChangeTier: (tier: SubscriptionTier) => Promise<void>;
}) {
  const [changing, setChanging] = useState(false);

  const handleChange = async (tier: string) => {
    setChanging(true);
    try {
      await onChangeTier(tier as SubscriptionTier);
    } finally {
      setChanging(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <Sparkles className="h-4 w-4 text-primary" aria-hidden />
          <CardTitle className="text-base">Subscription plan</CardTitle>
          <Badge variant="secondary">{SUBSCRIPTION_TIERS[currentTier].label}</Badge>
          {trialExpiresAt ? <Badge variant="warning">Trial</Badge> : null}
        </div>
        <CardDescription>
          Move to a lower plan any time, free. Moving to a higher plan may need real checkout below,
          under "Upgrade plan", if billing is configured for this shop.
          {trialExpiresAt ? (
            <span className="mt-1 block text-warning">
              This plan is a trial from a coupon - it reverts to Free on{' '}
              {new Date(trialExpiresAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })}
              {' '}unless you pick a plan below first.
            </span>
          ) : null}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <ul className="space-y-1 text-sm">
          {SUBSCRIPTION_TIERS[currentTier].features.map((feature) => (
            <li key={feature} className="flex items-center gap-2">
              <Check className="h-3.5 w-3.5 shrink-0 text-success" aria-hidden />
              {feature}
            </li>
          ))}
        </ul>
        <div className="flex items-center gap-2">
          <Select value={currentTier} onValueChange={handleChange} disabled={changing}>
            <SelectTrigger className="w-40"><SelectValue /></SelectTrigger>
            <SelectContent>
              {SUBSCRIPTION_TIER_OPTIONS.map((tier) => (
                <SelectItem key={tier} value={tier}>{SUBSCRIPTION_TIERS[tier].label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          {changing ? <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" aria-label="Changing plan" /> : null}
        </div>
      </CardContent>
    </Card>
  );
}

/**
 * CR-053 phase 1. A shop-wide default colour/font skin for the generated
 * invoice PDF - server-side, printed data, so it lives here on its own
 * instant-apply Select (same shape as SubscriptionPlanCard above), never
 * bundled into the per-person Appearance preference the AppearanceCard
 * below links out to.
 */
function InvoiceThemeCard({
  currentTheme, onChangeTheme,
}: {
  currentTheme: InvoiceTheme;
  onChangeTheme: (theme: InvoiceTheme) => Promise<void>;
}) {
  const [changing, setChanging] = useState(false);

  const handleChange = async (theme: string) => {
    setChanging(true);
    try {
      await onChangeTheme(theme as InvoiceTheme);
    } finally {
      setChanging(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <FileText className="h-4 w-4 text-primary" aria-hidden />
          <CardTitle className="text-base">Invoice PDF theme</CardTitle>
          <Badge variant="secondary">{INVOICE_THEMES[currentTheme].label}</Badge>
        </div>
        <CardDescription>
          The colour and font skin printed on every generated invoice PDF - applies to future PDFs
          immediately, no need to save the form below.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="flex items-center gap-2">
          <Select value={currentTheme} onValueChange={handleChange} disabled={changing}>
            <SelectTrigger className="w-40"><SelectValue /></SelectTrigger>
            <SelectContent>
              {INVOICE_THEME_OPTIONS.map((theme) => (
                <SelectItem key={theme} value={theme}>{INVOICE_THEMES[theme].label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          {changing ? <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" aria-label="Changing theme" /> : null}
        </div>
        <p className="text-sm text-muted-foreground">{INVOICE_THEMES[currentTheme].description}</p>
      </CardContent>
    </Card>
  );
}

/**
 * CR-053 backlog item 1 - myBillBook's "Additional Settings" panel. Each
 * checkbox saves instantly on toggle, the same "own instant-apply action"
 * shape as InvoiceThemeCard right above - these are shop-wide print/display
 * preferences, not part of the Branding/GST/Bank form below. The tagline
 * text field is the one exception: it saves on blur, not per keystroke.
 */
function AdditionalSettingsCard({
  settings, onChange,
}: {
  settings: TenantSettingsResponse;
  onChange: (patch: Partial<Pick<TenantSettingsResponse,
    'showItemDescription' | 'showAlternateUnit' | 'showPriceHistory' | 'enableFreeQuantity'
    | 'showInvoiceTime' | 'showItemImage' | 'invoiceTagline' | 'einvoiceEnabled'
      | 'paymentDueReminderEnabled' | 'lowStockAlertEnabled'>>) => Promise<void>;
}) {
  const [savingKey, setSavingKey] = useState<string | null>(null);
  const [tagline, setTagline] = useState(settings.invoiceTagline ?? '');
  useEffect(() => setTagline(settings.invoiceTagline ?? ''), [settings.invoiceTagline]);

  const toggle = async (
    key: 'showItemDescription' | 'showAlternateUnit' | 'showPriceHistory'
      | 'enableFreeQuantity' | 'showInvoiceTime' | 'showItemImage' | 'einvoiceEnabled'
      | 'paymentDueReminderEnabled' | 'lowStockAlertEnabled',
    value: boolean,
  ) => {
    setSavingKey(key);
    try {
      await onChange({ [key]: value });
    } finally {
      setSavingKey(null);
    }
  };

  const saveTagline = async () => {
    const trimmed = tagline.trim();
    if (trimmed === (settings.invoiceTagline ?? '')) return;
    setSavingKey('invoiceTagline');
    try {
      await onChange({ invoiceTagline: trimmed || null });
    } finally {
      setSavingKey(null);
    }
  };

  const toggles: { key: 'showItemDescription' | 'showAlternateUnit' | 'showPriceHistory'
    | 'enableFreeQuantity' | 'showInvoiceTime' | 'showItemImage' | 'einvoiceEnabled'
    | 'paymentDueReminderEnabled' | 'lowStockAlertEnabled';
    label: string; hint: string }[] = [
    { key: 'showItemDescription', label: 'Show item description',
      hint: 'Print each product\'s description under its name on the invoice.' },
    { key: 'showAlternateUnit', label: 'Show alternate unit',
      hint: 'Print a product\'s secondary unit (set on the product itself), e.g. "1 BOX = 12 PCS".' },
    { key: 'showPriceHistory', label: 'Show price history',
      hint: 'Show a Price History section on each product\'s detail page.' },
    { key: 'enableFreeQuantity', label: 'Enable free quantity',
      hint: 'Add a "Free Qty" field when billing an item, for bonus units given with a sale.' },
    { key: 'showInvoiceTime', label: 'Show time on invoices',
      hint: 'Print the time alongside the date on the invoice PDF.' },
    { key: 'showItemImage', label: 'Show item image',
      hint: 'Print a small product photo on each invoice line, where one is uploaded.' },
    { key: 'einvoiceEnabled', label: 'e-Invoice (GST IRN) review section - not yet live',
      hint: 'Show an e-Invoice review card on each invoice. Generating a real IRN needs a '
          + 'GSP/NIC account, which is not configured - the Generate button stays disabled '
          + 'with an honest message.' },
    { key: 'paymentDueReminderEnabled', label: 'Payment-due reminder',
      hint: 'Once a day, log an SMS reminder to your own shop number about outstanding invoice balances.' },
    { key: 'lowStockAlertEnabled', label: 'Low-stock alert',
      hint: 'Once a day, log an SMS alert to your own shop number when products are at or below reorder level.' },
  ];

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <Sparkles className="h-4 w-4 text-primary" aria-hidden />
          <CardTitle className="text-base">Additional settings</CardTitle>
        </div>
        <CardDescription>
          Optional invoice and product display preferences. Each change saves immediately.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {toggles.map(({ key, label, hint }) => (
          <div key={key} className="flex items-start gap-3">
            <Checkbox
              id={key}
              checked={settings[key]}
              disabled={savingKey === key}
              onCheckedChange={(checked) => void toggle(key, checked === true)}
              className="mt-0.5"
            />
            <label htmlFor={key} className="flex-1 cursor-pointer text-sm">
              <span className="font-medium">{label}</span>
              <p className="text-muted-foreground">{hint}</p>
            </label>
            {savingKey === key ? (
              <Loader2 className="h-4 w-4 shrink-0 animate-spin text-muted-foreground" aria-label="Saving" />
            ) : null}
          </div>
        ))}
        <div className="space-y-1.5 pt-1">
          <label htmlFor="invoiceTagline" className="text-sm font-medium">Invoice tagline</label>
          <p className="text-sm text-muted-foreground">A short line printed under your shop name, e.g. a motto.</p>
          <div className="flex items-center gap-2">
            <Input
              id="invoiceTagline"
              value={tagline}
              maxLength={255}
              placeholder="e.g. Trusted by builders since 1998"
              onChange={(e) => setTagline(e.target.value)}
              onBlur={() => void saveTagline()}
              disabled={savingKey === 'invoiceTagline'}
            />
            {savingKey === 'invoiceTagline' ? (
              <Loader2 className="h-4 w-4 shrink-0 animate-spin text-muted-foreground" aria-label="Saving" />
            ) : null}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

/**
 * CR-053 backlog item 3. Informational only, stated on the card itself as
 * well as in code - the computed TDS/TCS figure shown on a Purchase/
 * Invoice is never subtracted from or added to that document's own stored
 * total. Applying a statutory tax calculation to real financial totals is
 * its own separately-reviewed change, not a drive-by extension of this one.
 */
function TdsTcsCard({
  settings, onChange,
}: {
  settings: TenantSettingsResponse;
  onChange: (patch: Partial<Pick<TenantSettingsResponse,
    'tdsEnabled' | 'tdsSectionCode' | 'tdsRatePercent' | 'tcsEnabled' | 'tcsSectionCode' | 'tcsRatePercent'>>)
    => Promise<void>;
}) {
  const [savingKey, setSavingKey] = useState<string | null>(null);
  const [tdsSection, setTdsSection] = useState(settings.tdsSectionCode ?? '');
  const [tdsRate, setTdsRate] = useState(String(settings.tdsRatePercent));
  const [tcsSection, setTcsSection] = useState(settings.tcsSectionCode ?? '');
  const [tcsRate, setTcsRate] = useState(String(settings.tcsRatePercent));

  useEffect(() => setTdsSection(settings.tdsSectionCode ?? ''), [settings.tdsSectionCode]);
  useEffect(() => setTdsRate(String(settings.tdsRatePercent)), [settings.tdsRatePercent]);
  useEffect(() => setTcsSection(settings.tcsSectionCode ?? ''), [settings.tcsSectionCode]);
  useEffect(() => setTcsRate(String(settings.tcsRatePercent)), [settings.tcsRatePercent]);

  const toggle = async (key: 'tdsEnabled' | 'tcsEnabled', value: boolean) => {
    setSavingKey(key);
    try {
      await onChange({ [key]: value });
    } finally {
      setSavingKey(null);
    }
  };

  const saveTds = async () => {
    const rate = Number(tdsRate) || 0;
    const section = tdsSection.trim();
    if (section === (settings.tdsSectionCode ?? '') && rate === settings.tdsRatePercent) return;
    setSavingKey('tds');
    try {
      await onChange({ tdsSectionCode: section || null, tdsRatePercent: rate });
    } finally {
      setSavingKey(null);
    }
  };

  const saveTcs = async () => {
    const rate = Number(tcsRate) || 0;
    const section = tcsSection.trim();
    if (section === (settings.tcsSectionCode ?? '') && rate === settings.tcsRatePercent) return;
    setSavingKey('tcs');
    try {
      await onChange({ tcsSectionCode: section || null, tcsRatePercent: rate });
    } finally {
      setSavingKey(null);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">TDS / TCS</CardTitle>
        <CardDescription>
          Informational only - the figure is shown on the Purchase/Invoice detail page and PDF, but
          is never added to or deducted from that document&apos;s own total. You remain responsible
          for actually depositing and filing it.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="space-y-3 rounded-md border p-3">
          <div className="flex items-start gap-3">
            <Checkbox
              id="tdsEnabled"
              checked={settings.tdsEnabled}
              disabled={savingKey === 'tdsEnabled'}
              onCheckedChange={(checked) => void toggle('tdsEnabled', checked === true)}
              className="mt-0.5"
            />
            <label htmlFor="tdsEnabled" className="flex-1 cursor-pointer text-sm">
              <span className="font-medium">TDS on purchases</span>
              <p className="text-muted-foreground">Show how much you would withhold before paying a supplier.</p>
            </label>
            {savingKey === 'tdsEnabled' ? (
              <Loader2 className="h-4 w-4 shrink-0 animate-spin text-muted-foreground" aria-label="Saving" />
            ) : null}
          </div>
          {settings.tdsEnabled ? (
            <div className="grid grid-cols-2 gap-3 pl-7">
              <FormField id="tdsSectionCode" label="Section (e.g. 194Q)">
                <Input id="tdsSectionCode" value={tdsSection} maxLength={20}
                       onChange={(e) => setTdsSection(e.target.value)} onBlur={() => void saveTds()} />
              </FormField>
              <FormField id="tdsRatePercent" label="Rate (%)">
                <Input id="tdsRatePercent" inputMode="decimal" value={tdsRate}
                       onChange={(e) => setTdsRate(e.target.value)} onBlur={() => void saveTds()} />
              </FormField>
            </div>
          ) : null}
        </div>

        <div className="space-y-3 rounded-md border p-3">
          <div className="flex items-start gap-3">
            <Checkbox
              id="tcsEnabled"
              checked={settings.tcsEnabled}
              disabled={savingKey === 'tcsEnabled'}
              onCheckedChange={(checked) => void toggle('tcsEnabled', checked === true)}
              className="mt-0.5"
            />
            <label htmlFor="tcsEnabled" className="flex-1 cursor-pointer text-sm">
              <span className="font-medium">TCS on sales</span>
              <p className="text-muted-foreground">Show how much extra you would collect from a customer.</p>
            </label>
            {savingKey === 'tcsEnabled' ? (
              <Loader2 className="h-4 w-4 shrink-0 animate-spin text-muted-foreground" aria-label="Saving" />
            ) : null}
          </div>
          {settings.tcsEnabled ? (
            <div className="grid grid-cols-2 gap-3 pl-7">
              <FormField id="tcsSectionCode" label="Section (e.g. 206C(1H))">
                <Input id="tcsSectionCode" value={tcsSection} maxLength={20}
                       onChange={(e) => setTcsSection(e.target.value)} onBlur={() => void saveTcs()} />
              </FormField>
              <FormField id="tcsRatePercent" label="Rate (%)">
                <Input id="tcsRatePercent" inputMode="decimal" value={tcsRate}
                       onChange={(e) => setTcsRate(e.target.value)} onBlur={() => void saveTcs()} />
              </FormField>
            </div>
          ) : null}
        </div>
      </CardContent>
    </Card>
  );
}

/** CR-031 (Customer 360 §27-40) - usage against the tier's entitlement limits, with an upgrade prompt once any row is at or near its cap. -1 means unlimited (MAX tier). */
function UsageCard({ usage }: { usage: UsageSummaryResponse }) {
  const rows: { label: string; count: number; max: number }[] = [
    { label: 'Owners', count: usage.ownerCount, max: usage.maxOwners },
    { label: 'Customers', count: usage.customerCount, max: usage.maxCustomers },
    { label: 'Suppliers', count: usage.supplierCount, max: usage.maxSuppliers },
    { label: 'Products', count: usage.productCount, max: usage.maxProducts },
  ];
  const anyNearLimit = rows.some((row) => row.max > 0 && row.count / row.max >= 0.8);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Plan usage</CardTitle>
        <CardDescription>How much of your {SUBSCRIPTION_TIERS[usage.tier].label} plan's limits you&apos;re currently using.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {rows.map((row) => {
          const unlimited = row.max < 0;
          const percent = unlimited ? 0 : Math.min(100, Math.round((row.count / Math.max(row.max, 1)) * 100));
          const nearLimit = !unlimited && row.count / row.max >= 0.8;
          const atLimit = !unlimited && row.count >= row.max;
          return (
            <div key={row.label}>
              <div className="mb-1 flex items-center justify-between text-sm">
                <span className="text-muted-foreground">{row.label}</span>
                <span className={cn('tabular font-medium', atLimit && 'text-destructive', !atLimit && nearLimit && 'text-warning')}>
                  {row.count.toLocaleString('en-IN')} {unlimited ? '' : `/ ${row.max.toLocaleString('en-IN')}`}
                  {unlimited ? <span className="text-muted-foreground"> · Unlimited</span> : null}
                </span>
              </div>
              {unlimited ? null : (
                <div className="h-2 w-full overflow-hidden rounded-full bg-muted" role="progressbar"
                     aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100} aria-label={`${row.label} usage`}>
                  <div
                    className={cn('h-full rounded-full', atLimit ? 'bg-destructive' : nearLimit ? 'bg-warning' : 'bg-primary')}
                    style={{ width: `${percent}%` }}
                  />
                </div>
              )}
            </div>
          );
        })}
        {anyNearLimit ? (
          <p className="text-sm text-muted-foreground">
            Getting close to a limit? Upgrade the plan above for more room.
          </p>
        ) : null}
      </CardContent>
    </Card>
  );
}

/**
 * CR-033/CR-034 - theme applies on click (the theme providers write CSS
 * custom properties immediately, no server round trip), so there is
 * nothing to "save" here - it persists per signed-in user on this browser
 * (theme/themeScope.ts) the same way the pre-existing light/dark
 * ModeToggle already did. Deliberately not tenant-scoped server-side:
 * appearance is a per-person viewing preference (two staff on the same
 * shop's account might want different looks), not shop data. The full
 * design-style/colour/intensity/corner/elevation/motion controls live on
 * one dedicated page (AppearanceSettings, also reachable from My Profile ->
 * Appearance) rather than being duplicated inline here.
 */
/** CR-056 - links out to its own page, same shape as AppearanceCard below (a whole connect/disconnect/test flow does not fit inline in this form). */
function WhatsAppBusinessCard() {
  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <MessageCircle className="h-4 w-4 text-primary" aria-hidden />
          <CardTitle className="text-base">WhatsApp Business</CardTitle>
        </div>
        <CardDescription>
          Connect your own WhatsApp Business account to send invoices and payment reminders
          directly to your customers.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Button variant="outline" asChild>
          <Link to={SETTINGS_ROUTES.whatsapp}>
            <MessageCircle className="h-4 w-4" />
            Open WhatsApp Business settings
          </Link>
        </Button>
      </CardContent>
    </Card>
  );
}

function AppearanceCard() {
  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <Palette className="h-4 w-4 text-primary" aria-hidden />
          <CardTitle className="text-base">Appearance</CardTitle>
        </div>
        <CardDescription>
          Choose a design style, colour theme, light/dark mode and visual intensity for the whole app.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Button variant="outline" asChild>
          <Link to={AUTH_ROUTES.appearance}>
            <Palette className="h-4 w-4" />
            Open Theme &amp; Appearance
          </Link>
        </Button>
      </CardContent>
    </Card>
  );
}
