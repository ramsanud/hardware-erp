import { useState } from 'react';
import { Loader2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Label } from '@/shared/components/ui/label';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import { useToast } from '@/modules/auth/hooks/useToast';
import { ProductPicker } from '@/modules/invoice/components/ProductPicker';
import type { ProductSummaryResponse } from '@/modules/product/types';
import { useFeatureGate } from '@/modules/subscription/hooks/useFeatureGate';
import { UpgradeDialog } from '@/modules/subscription/components/UpgradeDialog';
import { substituteService } from '../services/substituteService';
import type { ProductRequestResponse } from '../types';

interface NewProductRequestDialogProps {
  open: boolean;
  onClose: () => void;
  onCreated: (request: ProductRequestResponse) => void;
}

/**
 * CR-089 §9. Records what the customer asked for. The quantity is required
 * (availability is judged against it); the budget and customer details are
 * optional and stay inside this shop - nothing here is ever sent to
 * another shop (CR-090's explicit rule).
 */
export function NewProductRequestDialog({ open, onClose, onCreated }: NewProductRequestDialogProps) {
  const toast = useToast();
  const gate = useFeatureGate();
  const [product, setProduct] = useState<ProductSummaryResponse | null>(null);
  const [quantity, setQuantity] = useState('1');
  const [budgetRupees, setBudgetRupees] = useState('');
  const [customerName, setCustomerName] = useState('');
  const [customerMobile, setCustomerMobile] = useState('');
  const [saving, setSaving] = useState(false);

  const reset = () => {
    setProduct(null);
    setQuantity('1');
    setBudgetRupees('');
    setCustomerName('');
    setCustomerMobile('');
  };

  const submit = async () => {
    if (!product) {
      toast.error(null, 'Pick the product the customer asked for.');
      return;
    }
    const qty = Number(quantity);
    if (!Number.isFinite(qty) || qty <= 0) {
      toast.error(null, 'Enter the quantity the customer wants.');
      return;
    }
    const budget = budgetRupees.trim() === '' ? null : Math.round(Number(budgetRupees) * 100);
    setSaving(true);
    try {
      const created = await gate.guard(() => substituteService.create({
        productId: product.id,
        requestedQuantity: qty,
        requestedBudgetPaise: budget,
        customerName: customerName.trim() || null,
        customerMobile: customerMobile.trim() || null,
      }));
      if (created) {
        toast.success(created.suggestions.length > 0
          ? `${created.suggestions.length} alternative${created.suggestions.length === 1 ? '' : 's'} found.`
          : 'Request recorded - no in-stock alternative matched.');
        reset();
        onCreated(created);
      }
    } catch (caught) {
      toast.error(caught, 'Could not record the request.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <Dialog open={open} onOpenChange={(next) => { if (!next) { reset(); onClose(); } }}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Customer asked for a product</DialogTitle>
            <DialogDescription>
              Record what was asked for. If it is out of stock, in-stock alternatives from your own catalogue are suggested for you to offer.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label>Product</Label>
              {product ? (
                <div className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                  <span className="truncate">
                    <span className="font-medium">{product.productName}</span>
                    <span className="text-muted-foreground"> · {product.productCode}</span>
                  </span>
                  <Button variant="ghost" size="sm" onClick={() => setProduct(null)}>Change</Button>
                </div>
              ) : (
                <ProductPicker onPick={setProduct} excludeIds={[]} />
              )}
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="pr-qty">Quantity wanted</Label>
                <Input id="pr-qty" type="number" min="0.0001" step="any" value={quantity}
                       onChange={(e) => setQuantity(e.target.value)} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="pr-budget">Budget (₹, optional)</Label>
                <Input id="pr-budget" type="number" min="0" step="1" value={budgetRupees}
                       placeholder="e.g. 200" onChange={(e) => setBudgetRupees(e.target.value)} />
              </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="pr-name">Customer name (optional)</Label>
                <Input id="pr-name" value={customerName} maxLength={200}
                       onChange={(e) => setCustomerName(e.target.value)} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="pr-mobile">Customer mobile (optional)</Label>
                <Input id="pr-mobile" inputMode="numeric" value={customerMobile} maxLength={10}
                       onChange={(e) => setCustomerMobile(e.target.value.replace(/\D/g, ''))} />
              </div>
            </div>
          </div>

          <DialogFooter className="gap-2 sm:gap-2">
            <Button variant="outline" onClick={() => { reset(); onClose(); }} disabled={saving}>Cancel</Button>
            <Button onClick={submit} disabled={saving || !product}>
              {saving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              Find alternatives
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      <UpgradeDialog details={gate.details} onClose={gate.close} />
    </>
  );
}
