import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Calculator } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { NumberInput } from '@/shared/components/ui/number-input';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogDescription,
} from '@/shared/components/ui/dialog';
import { FormField } from '@/shared/components/FormField';
import { ApiError } from '@/shared/types/api';
import { projectService } from '../services/projectService';
import { rooftopCalculatorSchema, type RooftopCalculatorValues } from '../validation/schemas';
import type { RooftopCalculatorResponse } from '../types';

interface RooftopCalculatorDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Required area + overlap% + wastage%, divided by one sheet's area,
 * rounded up (request §24-25) - a starting estimate only. The result is
 * shown here for the shop to read and enter by hand into the material's
 * quantity field, never auto-applied, so a user always sees and can
 * override the calculated number before it becomes a real line item.
 */
export function RooftopCalculatorDialog({ open, onOpenChange }: RooftopCalculatorDialogProps) {
  const [result, setResult] = useState<RooftopCalculatorResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const {
    control, handleSubmit, formState: { errors, isSubmitting },
  } = useForm<RooftopCalculatorValues>({
    resolver: zodResolver(rooftopCalculatorSchema),
    defaultValues: { overlapPercent: 10, wastagePercent: 5 },
  });

  const submit = handleSubmit(async (values) => {
    setError(null);
    try {
      setResult(await projectService.calculateRooftopSheets(values));
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not calculate. Please try again.');
    }
  });

  return (
    <Dialog open={open} onOpenChange={(next) => { onOpenChange(next); if (!next) setResult(null); }}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2"><Calculator className="h-4 w-4" /> Rooftop sheet calculator</DialogTitle>
          <DialogDescription>A starting estimate - always editable by hand once you add the material line.</DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} className="space-y-4" noValidate>
          {error ? <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert> : null}

          <div className="grid grid-cols-2 gap-4">
            <FormField id="widthMeters" label="Roof width (m)" error={errors.widthMeters?.message} required>
              <Controller control={control} name="widthMeters" render={({ field }) => (
                <NumberInput id="widthMeters" min={0} value={field.value} onChange={field.onChange} onBlur={field.onBlur} />
              )} />
            </FormField>
            <FormField id="lengthMeters" label="Roof length (m)" error={errors.lengthMeters?.message} required>
              <Controller control={control} name="lengthMeters" render={({ field }) => (
                <NumberInput id="lengthMeters" min={0} value={field.value} onChange={field.onChange} onBlur={field.onBlur} />
              )} />
            </FormField>
            <FormField id="sheetWidthMeters" label="Sheet width (m)" error={errors.sheetWidthMeters?.message} required>
              <Controller control={control} name="sheetWidthMeters" render={({ field }) => (
                <NumberInput id="sheetWidthMeters" min={0} value={field.value} onChange={field.onChange} onBlur={field.onBlur} />
              )} />
            </FormField>
            <FormField id="sheetLengthMeters" label="Sheet length (m)" error={errors.sheetLengthMeters?.message} required>
              <Controller control={control} name="sheetLengthMeters" render={({ field }) => (
                <NumberInput id="sheetLengthMeters" min={0} value={field.value} onChange={field.onChange} onBlur={field.onBlur} />
              )} />
            </FormField>
            <FormField id="overlapPercent" label="Overlap %">
              <Controller control={control} name="overlapPercent" render={({ field }) => (
                <NumberInput id="overlapPercent" min={0} value={field.value ?? 0} onChange={field.onChange} onBlur={field.onBlur} />
              )} />
            </FormField>
            <FormField id="wastagePercent" label="Wastage %">
              <Controller control={control} name="wastagePercent" render={({ field }) => (
                <NumberInput id="wastagePercent" min={0} value={field.value ?? 0} onChange={field.onChange} onBlur={field.onBlur} />
              )} />
            </FormField>
          </div>

          {result ? (
            <div className="rounded-md border bg-muted/40 p-4 text-sm space-y-1">
              <div className="flex justify-between"><span className="text-muted-foreground">Required area</span><span className="tabular">{result.requiredAreaSqMeters} m²</span></div>
              <div className="flex justify-between"><span className="text-muted-foreground">After overlap + wastage</span><span className="tabular">{result.areaAfterOverlapAndWastageSqMeters} m²</span></div>
              <div className="flex justify-between font-semibold"><span>Estimated sheets needed</span><span className="tabular">{result.calculatedSheetQuantity}</span></div>
            </div>
          ) : null}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Close</Button>
            <Button type="submit" loading={isSubmitting}>Calculate</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
