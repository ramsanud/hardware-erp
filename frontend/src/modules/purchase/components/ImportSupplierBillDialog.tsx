import { useRef, useState } from 'react';
import {
  AlertTriangle, Check, FileSpreadsheet, Upload, X,
} from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription,
} from '@/shared/components/ui/dialog';
import { Badge } from '@/shared/components/ui/badge';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import { FormField } from '@/shared/components/FormField';
import { SupplierPicker } from '@/modules/project/components/SupplierPicker';
import { categoryService } from '@/modules/product/services/categoryService';
import { brandService } from '@/modules/product/services/brandService';
import type { SupplierSummaryResponse } from '@/modules/supplier/types';
import { useToast } from '@/modules/auth/hooks/useToast';
import { ApiError } from '@/shared/types/api';
import { purchaseService } from '../services/purchaseService';
import type { ImportConfirmRow, ImportPreviewResponse, ImportResultResponse } from '../types';

type Stage = 'upload' | 'preview' | 'result';

interface ImportSupplierBillDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onImported: (purchaseId: number) => void;
}

const SUPPORTED_HINT = 'Supported today: CSV and Excel (.xlsx). PDF/image bills need a configured OCR/AI provider - enter those manually instead.';

/** Upload -> Preview (edit/match) -> Confirm -> Result. Never writes anything until Confirm Import is clicked (spec §45). */
export function ImportSupplierBillDialog({ open, onOpenChange, onImported }: ImportSupplierBillDialogProps) {
  const toast = useToast();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [stage, setStage] = useState<Stage>('upload');
  const [file, setFile] = useState<File | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [preview, setPreview] = useState<ImportPreviewResponse | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [askDiscard, setAskDiscard] = useState(false);
  const [duplicateWarning, setDuplicateWarning] = useState<string | null>(null);
  const [result, setResult] = useState<ImportResultResponse | null>(null);

  const [supplier, setSupplier] = useState<SupplierSummaryResponse | null>(null);
  const [supplierBillNumber, setSupplierBillNumber] = useState('');
  const [purchaseDate, setPurchaseDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [rows, setRows] = useState<ImportConfirmRow[]>([]);
  const [rowLinks, setRowLinks] = useState<Record<number, { brandId: number | null; categoryId: number | null }>>({});

  const reset = () => {
    setStage('upload'); setFile(null); setPreview(null); setResult(null);
    setSupplier(null); setSupplierBillNumber(''); setRows([]); setRowLinks({});
    setDuplicateWarning(null);
  };

  const handleClose = (nextOpen: boolean) => {
    if (!nextOpen && stage === 'preview') {
      setAskDiscard(true);
      return;
    }
    if (!nextOpen) reset();
    onOpenChange(nextOpen);
  };

  const pickFile = (picked: File | null) => {
    if (!picked) return;
    setFile(picked);
  };

  const runPreview = async () => {
    if (!file) return;
    setLoadingPreview(true);
    try {
      const response = await purchaseService.importPreview(file);
      setPreview(response);
      if (response.extractionAvailable) {
        setRows(response.rows.map((row) => ({
          rowNumber: row.rowNumber,
          existingProductId: row.matchedProductId,
          newProductName: row.productName,
          newProductSku: row.sku,
          newProductCategoryId: row.matchedCategoryId,
          newProductBrandId: row.matchedBrandId,
          newProductUnit: row.unit,
          quantity: row.quantity ?? 0,
          unitPricePaise: row.unitPricePaise ?? 0,
          gstRatePercent: row.gstRatePercent,
          updateExistingProductCost: false,
        })));
      }
      setStage('preview');
    } catch (caught) {
      toast.error(caught, 'Could not read this file.');
    } finally {
      setLoadingPreview(false);
    }
  };

  const linkBrand = async (row: ImportConfirmRow, brandName: string) => {
    try {
      const created = await brandService.create({ brandName, status: 'ACTIVE' });
      setRowLinks((current) => ({ ...current, [row.rowNumber]: { ...current[row.rowNumber], brandId: created.id, categoryId: current[row.rowNumber]?.categoryId ?? null } }));
      setRows((current) => current.map((r) => (r.rowNumber === row.rowNumber ? { ...r, newProductBrandId: created.id } : r)));
      toast.success(`Brand "${brandName}" created.`);
    } catch (caught) {
      toast.error(caught, 'Could not create this brand.');
    }
  };

  const linkCategory = async (row: ImportConfirmRow, categoryName: string) => {
    try {
      const created = await categoryService.create({ categoryName, status: 'ACTIVE' });
      setRowLinks((current) => ({ ...current, [row.rowNumber]: { ...current[row.rowNumber], categoryId: created.id, brandId: current[row.rowNumber]?.brandId ?? null } }));
      setRows((current) => current.map((r) => (r.rowNumber === row.rowNumber ? { ...r, newProductCategoryId: created.id } : r)));
      toast.success(`Category "${categoryName}" created.`);
    } catch (caught) {
      toast.error(caught, 'Could not create this category.');
    }
  };

  const runConfirm = async (confirmDuplicateAnyway: boolean) => {
    if (!file || !supplier) return;
    setConfirming(true);
    setDuplicateWarning(null);
    try {
      const response = await purchaseService.importConfirm(file, {
        supplierId: supplier.id,
        supplierBillNumber: supplierBillNumber || null,
        purchaseDate,
        rows,
        confirmDuplicateAnyway,
      });
      setResult(response);
      setStage('result');
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'DUPLICATE_BILL_SUSPECTED') {
        setDuplicateWarning(caught.message);
      } else {
        toast.error(caught, 'Could not import this bill.');
      }
    } finally {
      setConfirming(false);
    }
  };

  const allRowsClean = preview ? preview.rows.every((r) => r.errors.length === 0) : false;
  const canConfirm = Boolean(supplier) && purchaseDate && rows.length > 0 && allRowsClean && !confirming;

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-4xl">
        <DialogHeader>
          <DialogTitle>Import supplier bill</DialogTitle>
          <DialogDescription>Upload a bill, review what will be created or matched, then confirm.</DialogDescription>
        </DialogHeader>

        {stage === 'upload' ? (
          <div className="space-y-4">
            <div
              onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
              onDragLeave={() => setDragOver(false)}
              onDrop={(e) => { e.preventDefault(); setDragOver(false); pickFile(e.dataTransfer.files[0] ?? null); }}
              onClick={() => fileInputRef.current?.click()}
              className={`flex cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed p-10 text-center transition-colors ${dragOver ? 'border-primary bg-primary/5' : 'border-border'}`}
            >
              <Upload className="h-8 w-8 text-muted-foreground" aria-hidden />
              {file ? (
                <p className="flex items-center gap-2 text-sm font-medium"><FileSpreadsheet className="h-4 w-4" />{file.name}</p>
              ) : (
                <>
                  <p className="text-sm font-medium">Drag & drop a file here, or click to browse</p>
                  <p className="text-xs text-muted-foreground">{SUPPORTED_HINT}</p>
                </>
              )}
              <input
                ref={fileInputRef} type="file" accept=".csv,.xlsx" className="hidden"
                onChange={(e) => pickFile(e.target.files?.[0] ?? null)}
              />
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => handleClose(false)}>Close</Button>
              <Button onClick={runPreview} disabled={!file} loading={loadingPreview}>Preview</Button>
            </div>
          </div>
        ) : null}

        {stage === 'preview' && preview ? (
          !preview.extractionAvailable ? (
            <div className="space-y-4">
              <Alert>
                <AlertTriangle className="h-4 w-4" />
                <AlertDescription>{preview.message}</AlertDescription>
              </Alert>
              <div className="flex justify-end gap-2">
                <Button variant="outline" onClick={() => setStage('upload')}>Choose a different file</Button>
                <Button variant="outline" onClick={() => handleClose(false)}>Close</Button>
              </div>
            </div>
          ) : (
            <div className="space-y-4">
              {preview.message ? (
                <Alert><AlertTriangle className="h-4 w-4" /><AlertDescription>{preview.message}</AlertDescription></Alert>
              ) : null}
              {preview.warnings.map((warning, index) => (
                <Alert key={index}><AlertTriangle className="h-4 w-4" /><AlertDescription>{warning}</AlertDescription></Alert>
              ))}

              <div className="grid gap-4 sm:grid-cols-3">
                <div className="sm:col-span-1">
                  <p className="mb-1.5 text-sm font-medium">Supplier <span className="text-destructive">*</span></p>
                  {supplier ? (
                    <div className="flex items-center justify-between rounded-md border px-3 py-2 text-sm">
                      <span className="font-medium">{supplier.supplierName}</span>
                      <Button type="button" variant="ghost" size="sm" onClick={() => setSupplier(null)}>Change</Button>
                    </div>
                  ) : (
                    <SupplierPicker onPick={setSupplier} />
                  )}
                </div>
                <FormField id="importBillNumber" label="Supplier bill number">
                  <Input id="importBillNumber" value={supplierBillNumber} onChange={(e) => setSupplierBillNumber(e.target.value)} />
                </FormField>
                <FormField id="importDate" label="Purchase date" required>
                  <Input id="importDate" type="date" value={purchaseDate} onChange={(e) => setPurchaseDate(e.target.value)} />
                </FormField>
              </div>

              <div className="flex items-center gap-4 text-sm text-muted-foreground">
                <span>{preview.totalRows} rows</span>
                <span className="text-emerald-600 dark:text-emerald-400">{preview.existingProductCount} existing</span>
                <span className="text-primary">{preview.newProductCount} new</span>
                {preview.rowsWithErrors > 0 ? <span className="text-destructive">{preview.rowsWithErrors} with errors</span> : null}
              </div>

              <div className="max-h-[40vh] overflow-auto rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Row</TableHead>
                      <TableHead>Product</TableHead>
                      <TableHead>Brand</TableHead>
                      <TableHead>Category</TableHead>
                      <TableHead className="w-16">Qty</TableHead>
                      <TableHead className="w-24">Price</TableHead>
                      <TableHead className="w-16">GST%</TableHead>
                      <TableHead>Status</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {preview.rows.map((row) => {
                      const link = rowLinks[row.rowNumber];
                      const brandResolved = row.brandIsExisting || link?.brandId;
                      const categoryResolved = row.categoryIsExisting || link?.categoryId;
                      return (
                        <TableRow key={row.rowNumber} className={row.errors.length > 0 ? 'bg-destructive/5' : undefined}>
                          <TableCell className="text-xs text-muted-foreground">{row.rowNumber}</TableCell>
                          <TableCell className="font-medium">{row.productName ?? '—'}</TableCell>
                          <TableCell>
                            {row.brandName ? (
                              brandResolved ? (
                                <span className="text-xs">{row.brandName}</span>
                              ) : (
                                <Button type="button" variant="outline" size="sm" className="h-7 text-xs"
                                        onClick={() => linkBrand(rows.find((r) => r.rowNumber === row.rowNumber)!, row.brandName!)}>
                                  + Add "{row.brandName}"
                                </Button>
                              )
                            ) : <span className="text-xs text-muted-foreground">—</span>}
                          </TableCell>
                          <TableCell>
                            {row.categoryName ? (
                              categoryResolved ? (
                                <span className="text-xs">{row.categoryName}</span>
                              ) : (
                                <Button type="button" variant="outline" size="sm" className="h-7 text-xs"
                                        onClick={() => linkCategory(rows.find((r) => r.rowNumber === row.rowNumber)!, row.categoryName!)}>
                                  + Add "{row.categoryName}"
                                </Button>
                              )
                            ) : <span className="text-xs text-muted-foreground">—</span>}
                          </TableCell>
                          <TableCell className="tabular text-xs">{row.quantity ?? '—'}</TableCell>
                          <TableCell className="tabular text-xs">{row.unitPricePaise != null ? (row.unitPricePaise / 100).toFixed(2) : '—'}</TableCell>
                          <TableCell className="tabular text-xs">{row.gstRatePercent}</TableCell>
                          <TableCell>
                            {row.errors.length > 0 ? (
                              <span title={row.errors.join('; ')}>
                                <Badge variant="destructive">Error</Badge>
                              </span>
                            ) : row.productIsExisting ? (
                              <Badge variant="success">Existing</Badge>
                            ) : (
                              <Badge>New</Badge>
                            )}
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>

              {duplicateWarning ? (
                <Alert variant="destructive">
                  <AlertTriangle className="h-4 w-4" />
                  <AlertDescription>
                    {duplicateWarning}
                    <div className="mt-2 flex gap-2">
                      <Button type="button" size="sm" variant="outline" onClick={() => setDuplicateWarning(null)}>Review</Button>
                      <Button type="button" size="sm" variant="destructive" onClick={() => runConfirm(true)}>Continue anyway</Button>
                    </div>
                  </AlertDescription>
                </Alert>
              ) : null}

              <div className="flex justify-end gap-2 border-t pt-4">
                <Button variant="outline" onClick={() => setStage('upload')}>Back</Button>
                <Button variant="outline" onClick={() => handleClose(false)}>Cancel</Button>
                <Button onClick={() => runConfirm(false)} disabled={!canConfirm} loading={confirming}>
                  <Check className="h-4 w-4" />
                  Confirm import
                </Button>
              </div>
            </div>
          )
        ) : null}

        {stage === 'result' && result ? (
          <div className="space-y-4">
            <Alert>
              <Check className="h-4 w-4" />
              <AlertDescription>
                <p className="font-medium">Supplier bill imported successfully.</p>
              </AlertDescription>
            </Alert>
            <dl className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
              <div><dt className="text-muted-foreground">Rows imported</dt><dd className="font-medium">{result.rowsImported}</dd></div>
              <div><dt className="text-muted-foreground">Existing matched</dt><dd className="font-medium">{result.existingProductsMatched}</dd></div>
              <div><dt className="text-muted-foreground">New products</dt><dd className="font-medium">{result.newProductsCreated}</dd></div>
              {result.rowsMergedWithEarlierRow > 0 ? (
                <div><dt className="text-muted-foreground">Same as an earlier row</dt><dd className="font-medium">{result.rowsMergedWithEarlierRow}</dd></div>
              ) : null}
              <div><dt className="text-muted-foreground">Stock added</dt><dd className="font-medium">{result.stockAddedDisplay}</dd></div>
              <div><dt className="text-muted-foreground">Purchase total</dt><dd className="font-medium">₹{result.totalPurchaseDisplay}</dd></div>
              <div><dt className="text-muted-foreground">Purchase #</dt><dd className="font-medium">{result.purchaseNumber}</dd></div>
            </dl>
            <div className="flex justify-end gap-2 border-t pt-4">
              <Button variant="outline" onClick={() => { reset(); onOpenChange(false); }}>Close</Button>
              <Button onClick={() => { onImported(result.purchaseId); reset(); }}>View purchase</Button>
            </div>
          </div>
        ) : null}

        {askDiscard ? (
          <div className="absolute inset-0 flex items-center justify-center rounded-lg bg-background/95 p-6">
            <div className="max-w-sm space-y-4 text-center">
              <AlertTriangle className="mx-auto h-8 w-8 text-warning" />
              <p className="font-medium">Discard imported data?</p>
              <p className="text-sm text-muted-foreground">Nothing has been saved yet - closing now discards everything you've reviewed.</p>
              <div className="flex justify-center gap-2">
                <Button variant="outline" onClick={() => setAskDiscard(false)}>Keep editing</Button>
                <Button variant="destructive" onClick={() => { setAskDiscard(false); reset(); onOpenChange(false); }}>
                  <X className="h-4 w-4" />Discard
                </Button>
              </div>
            </div>
          </div>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}
