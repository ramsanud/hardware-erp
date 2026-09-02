import { useMemo, useState } from 'react';
import { Calculator } from 'lucide-react';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/shared/components/ui/card';
import { NumberInput } from '@/shared/components/ui/number-input';
import { FormField } from '@/shared/components/FormField';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import { PageHeader } from '@/shared/components/PageHeader';

const GST_SLABS = [0, 5, 12, 18, 28] as const;
const CUSTOM = '__custom__';

function rupees(value: number): string {
  if (!Number.isFinite(value)) return '0.00';
  return new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);
}

/**
 * CR-053 backlog item 7. Pure client-side arithmetic - no backend, no
 * tenant data, nothing saved. Cost price + profit margin (on cost) + GST
 * rate -> the selling price a counter would actually key into an invoice
 * line, plus a donut breaking the final price down into cost/profit/GST so
 * the owner sees where the money in that number actually goes.
 */
export function GstCalculatorPage() {
  const [costPriceRupees, setCostPriceRupees] = useState(0);
  const [marginPercent, setMarginPercent] = useState(20);
  const [gstSlab, setGstSlab] = useState<string>('18');
  const [customGstPercent, setCustomGstPercent] = useState(18);

  const gstPercent = gstSlab === CUSTOM ? customGstPercent : Number(gstSlab);

  const result = useMemo(() => {
    const cost = Math.max(costPriceRupees, 0);
    const margin = Math.max(marginPercent, 0);
    const gst = Math.max(gstPercent, 0);

    const profit = cost * (margin / 100);
    const sellingPriceExGst = cost + profit;
    const gstAmount = sellingPriceExGst * (gst / 100);
    const finalPrice = sellingPriceExGst + gstAmount;

    return { cost, profit, sellingPriceExGst, gstAmount, finalPrice };
  }, [costPriceRupees, marginPercent, gstPercent]);

  const chartData = [
    { label: 'Cost price', value: result.cost, key: 'cost' },
    { label: 'Profit', value: result.profit, key: 'profit' },
    { label: 'GST', value: result.gstAmount, key: 'gst' },
  ].filter((slice) => slice.value > 0);

  return (
    <>
      <PageHeader
        title="GST & margin calculator"
        description="Work out a selling price from cost, profit margin and GST rate - nothing here is saved."
      />

      <div className="grid gap-5 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <Calculator className="h-4 w-4 text-primary" aria-hidden />
              <CardTitle className="text-base">Inputs</CardTitle>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <FormField id="costPrice" label="Cost price (₹)">
              <NumberInput id="costPrice" min={0} value={costPriceRupees} onChange={setCostPriceRupees} />
            </FormField>

            <FormField id="marginPercent" label="Profit margin on cost (%)"
                       hint="e.g. 20 means you add 20% of the cost price as profit">
              <NumberInput id="marginPercent" min={0} max={1000} value={marginPercent} onChange={setMarginPercent} />
            </FormField>

            <FormField id="gstSlab" label="GST rate">
              <Select value={gstSlab} onValueChange={setGstSlab}>
                <SelectTrigger id="gstSlab"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {GST_SLABS.map((slab) => (
                    <SelectItem key={slab} value={String(slab)}>{slab}%</SelectItem>
                  ))}
                  <SelectItem value={CUSTOM}>Custom</SelectItem>
                </SelectContent>
              </Select>
            </FormField>

            {gstSlab === CUSTOM ? (
              <FormField id="customGst" label="Custom GST rate (%)">
                <NumberInput id="customGst" min={0} max={100} value={customGstPercent} onChange={setCustomGstPercent} />
              </FormField>
            ) : null}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Result</CardTitle>
            <CardDescription>The final price is what a customer pays; the donut shows what it is made of.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div className="rounded-md border p-3">
                <p className="text-muted-foreground">Selling price (before GST)</p>
                <p className="tabular text-lg font-semibold">₹{rupees(result.sellingPriceExGst)}</p>
              </div>
              <div className="rounded-md border p-3">
                <p className="text-muted-foreground">GST amount</p>
                <p className="tabular text-lg font-semibold">₹{rupees(result.gstAmount)}</p>
              </div>
              <div className="rounded-md border p-3">
                <p className="text-muted-foreground">Profit</p>
                <p className="tabular text-lg font-semibold text-success">₹{rupees(result.profit)}</p>
              </div>
              <div className="rounded-md border-2 border-primary/40 bg-primary/5 p-3">
                <p className="text-muted-foreground">Final price (incl. GST)</p>
                <p className="tabular text-lg font-semibold text-primary">₹{rupees(result.finalPrice)}</p>
              </div>
            </div>

            {chartData.length > 0 && result.finalPrice > 0 ? (
              <div>
                <div className="h-[200px] w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={chartData}
                        dataKey="value"
                        nameKey="label"
                        cx="50%"
                        cy="50%"
                        innerRadius={55}
                        outerRadius={85}
                        paddingAngle={2}
                        stroke="hsl(var(--card))"
                        strokeWidth={2}
                      >
                        {chartData.map((slice, index) => (
                          <Cell key={slice.key} fill={`hsl(var(--chart-${(index % 5) + 1}))`} />
                        ))}
                      </Pie>
                      <Tooltip
                        content={({ active, payload }) => {
                          if (!active || !payload?.length) return null;
                          const slice = payload[0].payload as { label: string; value: number };
                          const share = result.finalPrice > 0 ? (slice.value / result.finalPrice) * 100 : 0;
                          return (
                            <div className="surface-overlay rounded-md border px-3 py-2 text-xs">
                              <p className="font-medium">{slice.label}</p>
                              <p className="tabular mt-0.5">₹{rupees(slice.value)}</p>
                              <p className="mt-0.5 text-muted-foreground">{share.toFixed(1)}% of final price</p>
                            </div>
                          );
                        }}
                      />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
                <ul className="mt-2 flex flex-wrap justify-center gap-x-4 gap-y-1 text-xs">
                  {chartData.map((slice, index) => (
                    <li key={slice.key} className="flex items-center gap-1.5">
                      <span
                        className="h-2.5 w-2.5 shrink-0 rounded-full"
                        style={{ background: `hsl(var(--chart-${(index % 5) + 1}))` }}
                        aria-hidden
                      />
                      <span className="text-muted-foreground">{slice.label}</span>
                    </li>
                  ))}
                </ul>
              </div>
            ) : (
              <p className="py-8 text-center text-sm text-muted-foreground">
                Enter a cost price above to see the breakdown.
              </p>
            )}
          </CardContent>
        </Card>
      </div>
    </>
  );
}
