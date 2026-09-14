import { Link } from 'react-router-dom';
import { Lightbulb, Plus } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Card, CardContent } from '@/shared/components/ui/card';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import { INVOICE_ROUTES } from '@/modules/invoice/constants';
import { QUOTATION_ROUTES } from '@/modules/quotation/constants';
import { PRODUCT_ROUTES } from '@/modules/product/constants';
import { CUSTOMER_ROUTES } from '@/modules/customer/constants';

/**
 * CR-082. The four things a counter does most, one tap away. Each is gated
 * by the permission its destination needs - a button that lands on a 403 is
 * worse than no button - so a storekeeper sees only "Add product".
 */
export function QuickActionsCard() {
  // The mockup tints this card faintly in the brand colour (its bg-emerald-50/40
  // + border-emerald-100), which on tokens is primary at 5% over a 20% border.
  return (
    <Card className="border-primary/20 bg-primary/5">
      <CardContent className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3.5">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
            <Lightbulb className="h-5 w-5" aria-hidden />
          </span>
          <div>
            <p className="text-base font-semibold">Quick actions</p>
            <p className="text-xs text-muted-foreground">Get things done faster</p>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <PermissionGate permission={PERMISSIONS.QUOTATION_MANAGE}>
            <Button variant="outline" size="sm" asChild>
              <Link to={QUOTATION_ROUTES.create}><Plus className="h-4 w-4" />New quotation</Link>
            </Button>
          </PermissionGate>
          <PermissionGate permission={PERMISSIONS.INVOICE_CREATE}>
            <Button variant="outline" size="sm" asChild>
              <Link to={INVOICE_ROUTES.create}><Plus className="h-4 w-4" />New invoice</Link>
            </Button>
          </PermissionGate>
          <PermissionGate permission={PERMISSIONS.PRODUCT_MANAGE}>
            <Button variant="outline" size="sm" asChild>
              <Link to={PRODUCT_ROUTES.create}><Plus className="h-4 w-4" />Add product</Link>
            </Button>
          </PermissionGate>
          <PermissionGate permission={PERMISSIONS.CUSTOMER_MANAGE}>
            <Button variant="outline" size="sm" asChild>
              <Link to={CUSTOMER_ROUTES.create}><Plus className="h-4 w-4" />Add customer</Link>
            </Button>
          </PermissionGate>
        </div>
      </CardContent>
    </Card>
  );
}
