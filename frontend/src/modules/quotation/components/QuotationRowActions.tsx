import { useRef, useState } from 'react';
import {
  FileText, MessageCircle, MoreHorizontal, Pencil, Receipt, Trash2,
} from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/shared/components/ui/dropdown-menu';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { useToast } from '@/modules/auth/hooks/useToast';
import { PERMISSIONS } from '@/modules/auth/constants';
import { whatsAppLinkService, type WhatsAppLinkResponse } from '@/modules/notification/services/whatsAppLinkService';
import { previewBlob } from '@/shared/lib/utils';
import { quotationService } from '../services/quotationService';
import type { QuotationSummaryResponse } from '../types';

interface QuotationRowActionsProps {
  row: QuotationSummaryResponse;
  onConvert: (row: QuotationSummaryResponse) => void;
  onEdit: (row: QuotationSummaryResponse) => void;
  onDelete: (row: QuotationSummaryResponse) => void;
}

/**
 * CR-083. The "..." menu on each row. Every item follows the same rule the
 * detail page and the API already apply, so nothing here offers an action
 * the server would refuse:
 *
 *   PDF / WhatsApp   any status (QUOTATION_VIEW got you to this page)
 *   Convert          DRAFT / SENT / ACCEPTED and not expired, INVOICE_CREATE
 *   Edit             DRAFT / SENT, QUOTATION_MANAGE
 *   Delete           DRAFT only, QUOTATION_MANAGE - anything issued is rejected, not deleted
 */
export function QuotationRowActions({ row, onConvert, onEdit, onDelete }: QuotationRowActionsProps) {
  const { hasPermission } = useAuth();
  const toast = useToast();
  const [busy, setBusy] = useState<'pdf' | 'whatsapp' | null>(null);
  // Same trick as WhatsAppButton: fetch the wa.me link while the pointer is
  // still on the item, so the click that follows opens the tab synchronously
  // enough for popup blockers. One in-flight promise, never a stale cache.
  const pendingLink = useRef<Promise<WhatsAppLinkResponse> | null>(null);

  const canManage = hasPermission(PERMISSIONS.QUOTATION_MANAGE);
  const live = row.status === 'DRAFT' || row.status === 'SENT' || row.status === 'ACCEPTED';
  const canConvert = live && !row.expired && hasPermission(PERMISSIONS.INVOICE_CREATE);
  const canEdit = canManage && (row.status === 'DRAFT' || row.status === 'SENT');
  const canDelete = canManage && row.status === 'DRAFT';

  const resolveLink = () => {
    if (!pendingLink.current) {
      pendingLink.current = whatsAppLinkService.quotation(row.id).catch((error: unknown) => {
        pendingLink.current = null;
        throw error;
      });
    }
    return pendingLink.current;
  };

  const openPdf = async () => {
    setBusy('pdf');
    try {
      previewBlob(await quotationService.pdf(row.id));
    } catch (caught) {
      toast.error(caught, 'Could not open the quotation PDF.');
    } finally {
      setBusy(null);
    }
  };

  const openWhatsApp = async () => {
    setBusy('whatsapp');
    try {
      const link = await resolveLink();
      pendingLink.current = null;
      window.open(link.url, '_blank', 'noopener,noreferrer');
    } catch (caught) {
      toast.error(caught, 'Unable to prepare the WhatsApp message.');
    } finally {
      setBusy(null);
    }
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className="h-8 w-8" loading={busy !== null}
                aria-label={`Actions for ${row.quotationNumber}`}>
          <MoreHorizontal className="h-4 w-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-52">
        <DropdownMenuItem onClick={() => void openPdf()}>
          <FileText className="h-4 w-4" />
          View / Print PDF
        </DropdownMenuItem>
        <DropdownMenuItem
          onClick={() => void openWhatsApp()}
          onPointerEnter={() => { void resolveLink().catch(() => undefined); }}
          onFocus={() => { void resolveLink().catch(() => undefined); }}
          disabled={!row.customerMobile}
        >
          <MessageCircle className="h-4 w-4" />
          Send via WhatsApp
        </DropdownMenuItem>
        {canConvert ? (
          <DropdownMenuItem onClick={() => onConvert(row)}>
            <Receipt className="h-4 w-4" />
            Convert to invoice
          </DropdownMenuItem>
        ) : null}
        {canEdit || canDelete ? <DropdownMenuSeparator /> : null}
        {canEdit ? (
          <DropdownMenuItem onClick={() => onEdit(row)}>
            <Pencil className="h-4 w-4" />
            Edit quote
          </DropdownMenuItem>
        ) : null}
        {canDelete ? (
          <DropdownMenuItem destructive onClick={() => onDelete(row)}>
            <Trash2 className="h-4 w-4" />
            Delete draft
          </DropdownMenuItem>
        ) : null}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
