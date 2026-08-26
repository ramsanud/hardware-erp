/**
 * A picklist, not a validated master (CR-023) - `supplier.bank_name` stays
 * plain VARCHAR, no schema change. "Other" reveals a free-text field so an
 * unlisted bank is never blocked.
 */
export const INDIAN_BANKS: string[] = [
  'State Bank of India',
  'HDFC Bank',
  'ICICI Bank',
  'Axis Bank',
  'Punjab National Bank',
  'Bank of Baroda',
  'Canara Bank',
  'Union Bank of India',
  'Indian Bank',
  'Bank of India',
  'Central Bank of India',
  'Indian Overseas Bank',
  'UCO Bank',
  'Bank of Maharashtra',
  'Punjab and Sind Bank',
  'IDBI Bank',
  'Kotak Mahindra Bank',
  'IndusInd Bank',
  'Yes Bank',
  'IDFC FIRST Bank',
  'Federal Bank',
  'South Indian Bank',
  'Karur Vysya Bank',
  'City Union Bank',
  'RBL Bank',
  'Karnataka Bank',
  'Tamilnad Mercantile Bank',
  'Dhanlaxmi Bank',
  'Jammu and Kashmir Bank',
];

export const BANK_OTHER = 'Other';
