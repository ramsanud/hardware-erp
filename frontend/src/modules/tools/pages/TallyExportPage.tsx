import { useState } from 'react';
import { FileDown, Loader2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/shared/components/ui/card';
import { FormField } from '@/shared/components/FormField';
import { PageHeader } from '@/shared/components/PageHeader';
import { ApiError } from '@/shared/types/api';
import { downloadBlob } from '@/shared/lib/utils';
import { exportService } from '../services/exportService';

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

function firstOfMonth(): string {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10);
}

/**
 * CR-053 backlog item 2. See TallyXmlBuilder's own javadoc for exactly what
 * this does and does not cover - ledger-level accounting vouchers and
 * party/stock-item masters, not item-wise inventory vouchers, and the
 * sign convention has not been verified against real Tally software
 * (none exists in this environment). Said here too, not just in code,
 * because "export to Tally" is exactly the kind of claim that should not
 * overstate itself.
 */
export function TallyExportPage() {
  const [fromDate, setFromDate] = useState(firstOfMonth());
  const [toDate, setToDate] = useState(today());
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleExport = async () => {
    setError(null);
    setExporting(true);
    try {
      const blob = await exportService.tallyXml(fromDate, toDate);
      downloadBlob(blob, `tally-export-${fromDate}-to-${toDate}.xml`);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not generate the export.');
    } finally {
      setExporting(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Tally export"
        description="Sales, purchases, parties and stock items for a date range, as a Tally-importable XML file."
      />

      <Card className="max-w-lg">
        <CardHeader>
          <div className="flex items-center gap-2">
            <FileDown className="h-4 w-4 text-primary" aria-hidden />
            <CardTitle className="text-base">Export</CardTitle>
          </div>
          <CardDescription>
            Produces ledger-level Sales and Purchase vouchers, plus Customer/Supplier ledgers and
            Stock Item masters - not item-wise inventory vouchers. Import it into a test company in
            Tally first before trusting it against your real books.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {error ? (
            <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert>
          ) : null}

          <div className="grid gap-4 sm:grid-cols-2">
            <FormField id="fromDate" label="From date">
              <Input id="fromDate" type="date" value={fromDate} max={toDate}
                     onChange={(e) => setFromDate(e.target.value)} />
            </FormField>
            <FormField id="toDate" label="To date">
              <Input id="toDate" type="date" value={toDate} min={fromDate} max={today()}
                     onChange={(e) => setToDate(e.target.value)} />
            </FormField>
          </div>

          <Button onClick={() => void handleExport()} loading={exporting} className="w-full">
            <FileDown className="h-4 w-4" />
            Download Tally XML
          </Button>
          {exporting ? (
            <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <Loader2 className="h-3 w-3 animate-spin" aria-hidden />
              Building the export...
            </p>
          ) : null}
        </CardContent>
      </Card>
    </>
  );
}
