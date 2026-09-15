import { Navigate, useNavigate, useParams } from 'react-router-dom';
import {
  BookOpen, Boxes, FileJson, Hourglass, Receipt, Scale,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { PageHeader } from '@/shared/components/PageHeader';
import { Tabs, TabsList, TabsTrigger } from '@/shared/components/ui/tabs';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { REPORT_ROUTES } from '../constants';
import type { ReportKey } from '../constants';
import {
  DayBookPanel, GstSummaryPanel, PurchaseRegisterPanel, ReceivablesAgeingPanel, StockValuationPanel,
} from '../components/ReportPanels';
import { Gstr1Panel } from '../components/Gstr1Panel';

/*
  CR-086 / CR-087. One screen, one report at a time, the report in the URL
  so a tab can be bookmarked or sent to the accountant. GSTR-1 is a filing,
  not a report, and sits behind REPORT_FINANCIAL - the tab only appears for
  a role that holds it, and the route guard on the API refuses it anyway.
*/

interface ReportTab {
  key: ReportKey;
  label: string;
  description: string;
  icon: LucideIcon;
  permission?: string;
  panel: () => JSX.Element;
}

const TABS: ReportTab[] = [
  { key: 'day-book', label: 'Day Book', description: 'Every sale, receipt, credit note, purchase and expense, in date order.', icon: BookOpen, panel: DayBookPanel },
  { key: 'receivables-ageing', label: 'Receivables', description: 'Who owes what, and how long it has been owed.', icon: Hourglass, panel: ReceivablesAgeingPanel },
  { key: 'stock-valuation', label: 'Stock Valuation', description: 'What is on the shelf today, at cost and at selling price.', icon: Boxes, panel: StockValuationPanel },
  { key: 'purchase-register', label: 'Purchase Register', description: 'Supplier bills for a period with the tax split, paid and balance.', icon: Receipt, panel: PurchaseRegisterPanel },
  { key: 'gst-summary', label: 'GST Summary', description: 'Output tax, credit notes and input tax by rate, and the net.', icon: Scale, panel: GstSummaryPanel },
  { key: 'gstr1', label: 'GSTR-1', description: 'The monthly outward-supply return, as a file for the GST offline tool.', icon: FileJson, permission: PERMISSIONS.REPORT_FINANCIAL, panel: Gstr1Panel },
];

export function ReportsPage() {
  const { report } = useParams<{ report?: string }>();
  const navigate = useNavigate();
  const { hasPermission } = useAuth();

  const visible = TABS.filter((tab) => !tab.permission || hasPermission(tab.permission));
  const active = visible.find((tab) => tab.key === report);
  if (!active) {
    return <Navigate to={`${REPORT_ROUTES.list}/${visible[0].key}`} replace />;
  }
  const Panel = active.panel;

  return (
    <>
      <PageHeader title="Reports" description={active.description} />

      <Tabs value={active.key} onValueChange={(key) => navigate(`${REPORT_ROUTES.list}/${key}`)} className="space-y-4">
        <div className="overflow-x-auto pb-1">
          <TabsList aria-label="Reports" data-report-tabs>
            {visible.map((tab) => (
              <TabsTrigger key={tab.key} value={tab.key} className="gap-1.5" data-report-tab={tab.key}>
                <tab.icon className="h-3.5 w-3.5" aria-hidden />
                {tab.label}
              </TabsTrigger>
            ))}
          </TabsList>
        </div>
        <div key={active.key} data-report-panel={active.key}>
          <Panel />
        </div>
      </Tabs>
    </>
  );
}
