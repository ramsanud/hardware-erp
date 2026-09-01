import type { InvoiceTheme } from '../types';

/** Mirrors backend/invoice/pdf/InvoicePdfService.java's tokensFor() - keep the two in sync by hand. */
export const INVOICE_THEMES: Record<InvoiceTheme, { label: string; description: string }> = {
  CLASSIC: { label: 'Classic', description: 'Navy header, the default look every invoice has always had.' },
  MINIMAL: { label: 'Minimal', description: 'Mostly monochrome, thin rules, no filled header band.' },
  BOLD: { label: 'Bold', description: 'A strong orange header and accents.' },
  ELEGANT: { label: 'Elegant', description: 'Serif type on a warm gold/maroon palette.' },
};

export const INVOICE_THEME_OPTIONS: InvoiceTheme[] = ['CLASSIC', 'MINIMAL', 'BOLD', 'ELEGANT'];
