import { useRef, useState } from 'react';
import {
  AlertTriangle, Check, FileSpreadsheet, Upload, X,
} from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription,
} from '@/shared/components/ui/dialog';
import { Badge } from '@/shared/components/ui/badge';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import { useToast } from '@/modules/auth/hooks/useToast';
import { productService } from '../services/productService';
import type { ProductImportConfirmRow, ProductImportPreviewResponse, ProductImportResultResponse } from '../types';

type Stage = 'upload' | 'preview' | 'result';

interface ImportProductsDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onImported: () => void;
}

const SUPPORTED_HINT = 'Supported today: CSV and Excel (.xlsx). Expected columns: Product Name, Product Code, Category, Brand, Unit, HSN Code, GST %, Purchase Price, Selling Price, MRP, Minimum Stock, Reorder Level.';

/** Bulk product upload (CR-036) - same Upload -> Preview -> Confirm -> Result design as the Purchase module's Supplier Bill Import. Never writes anything until Confirm import is clicked. */
export function ImportProductsDialog({ open, onOpenChange, onImported }: ImportProductsDialogProps) {
  const toast = useToast();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [stage, setStage] = useState<Stage>('upload');
  const [file, setFile] = useState<File | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [preview, setPreview] = useState<ProductImportPreviewResponse | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [askDiscard, setAskDiscard] = useState(false);
  const [result, setResult] = useState<ProductImportResultResponse | null>(null);

  const reset = () => {
    setStage('upload'); setFile(null); setPreview(null); setResult(null);
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
      const response = await productService.importPreview(file);
      setPreview(response);
      setStage('preview');
    } catch (caught) {
      toast.error(caught, 'Could not read this file.');
    } finally {
      setLoadingPreview(false);
    }
  };

  const runConfirm = async () => {
    if (!preview) return;
    const cleanRows: ProductImportConfirmRow[] = preview.rows
      .filter((row) => row.errors.length === 0)
      .map((row) => ({
        rowNumber: row.rowNumber,
        productName: row.productName ?? '',
        productCode: row.productCode,
        categoryId: row.matchedCategoryId,
        brandId: row.matchedBrandId,
        unit: row.unit ?? '',
        hsnCode: row.hsnCode,
        gstRatePercent: row.gstRatePercent ?? 0,
        purchasePriceRupees: row.purchasePriceRupees ?? 0,
        sellingPriceRupees: row.sellingPriceRupees ?? 0,
        mrpRupees: row.mrpRupees ?? 0,
        minimumStock: row.minimumStock,
        reorderLevel: row.reorderLevel,
      }));
    if (cleanRows.length === 0) return;
    setConfirming(true);
    try {
      const response = await productService.importConfirm({ rows: cleanRows });
      setResult(response);
      setStage('result');
    } catch (caught) {
      toast.error(caught, 'Could not import these products.');
    } finally {
      setConfirming(false);
    }
  };

  const errorRowCount = preview?.rowsWithErrors ?? 0;
  const cleanRowCount = preview ? preview.rows.length - errorRowCount : 0;
  const canConfirm = cleanRowCount > 0 && !confirming;

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-4xl">
        <DialogHeader>
          <DialogTitle>Import products</DialogTitle>
          <DialogDescription>Upload a spreadsheet, review what will be created, then confirm.</DialogDescription>
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

              <div className="flex items-center gap-4 text-sm text-muted-foreground">
                <span>{preview.totalRows} rows</span>
                <span className="text-primary">{cleanRowCount} ready to import</span>
                {errorRowCount > 0 ? <span className="text-destructive">{errorRowCount} with errors (skipped)</span> : null}
              </div>

              <div className="max-h-[40vh] overflow-auto rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Row</TableHead>
                      <TableHead>Product</TableHead>
                      <TableHead>Code</TableHead>
                      <TableHead>Category</TableHead>
                      <TableHead>Brand</TableHead>
                      <TableHead className="w-16">Unit</TableHead>
                      <TableHead className="w-16">GST%</TableHead>
                      <TableHead className="w-20">Selling</TableHead>
                      <TableHead>Status</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {preview.rows.map((row) => (
                      <TableRow key={row.rowNumber} className={row.errors.length > 0 ? 'bg-destructive/5' : undefined}>
                        <TableCell className="text-xs text-muted-foreground">{row.rowNumber}</TableCell>
                        <TableCell className="font-medium">{row.productName ?? '—'}</TableCell>
                        <TableCell className="text-xs">{row.productCode ?? '—'}</TableCell>
                        <TableCell className="text-xs">
                          {row.categoryName ? (row.matchedCategoryId ? row.categoryName : `${row.categoryName} (not found)`) : '—'}
                        </TableCell>
                        <TableCell className="text-xs">
                          {row.brandName ? (row.matchedBrandId ? row.brandName : `${row.brandName} (not found)`) : '—'}
                        </TableCell>
                        <TableCell className="text-xs">{row.unit ?? '—'}</TableCell>
                        <TableCell className="tabular text-xs">{row.gstRatePercent ?? '—'}</TableCell>
                        <TableCell className="tabular text-xs">{row.sellingPriceRupees ?? '—'}</TableCell>
                        <TableCell>
                          {row.errors.length > 0 ? (
                            <span title={row.errors.join('; ')}>
                              <Badge variant="destructive">Error</Badge>
                            </span>
                          ) : (
                            <Badge>New</Badge>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>

              <div className="flex justify-end gap-2 border-t pt-4">
                <Button variant="outline" onClick={() => setStage('upload')}>Back</Button>
                <Button variant="outline" onClick={() => handleClose(false)}>Cancel</Button>
                <Button onClick={runConfirm} disabled={!canConfirm} loading={confirming}>
                  <Check className="h-4 w-4" />
                  Confirm import ({cleanRowCount})
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
                <p className="font-medium">{result.productsCreated} product{result.productsCreated === 1 ? '' : 's'} created.</p>
              </AlertDescription>
            </Alert>
            <div className="flex justify-end gap-2 border-t pt-4">
              <Button onClick={() => { onImported(); reset(); }}>Done</Button>
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
