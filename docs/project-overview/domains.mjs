// Curated grouping of the schema into business domains for the ER diagram.
// Every table name here is checked against the parsed migrations at build
// time (buildErd throws if one is missing or if a real table got left out
// of both the diagram and the appendix) so this list cannot silently drift
// from the actual schema.
export const DOMAIN_GROUPS = [
  {
    id: 'identity',
    label: 'Identity & Tenant',
    color: '#6d5ce8',
    tables: ['tenant', 'app_user', 'role', 'permission', 'role_permission', 'refresh_token'],
  },
  {
    id: 'catalog',
    label: 'Catalog',
    color: '#1f8a70',
    tables: ['category', 'brand', 'product', 'product_image'],
  },
  {
    id: 'parties',
    label: 'Parties',
    color: '#b8860b',
    tables: ['customer', 'supplier', 'supplier_contact'],
  },
  {
    id: 'procure',
    label: 'Purchase & Inventory',
    color: '#c1440e',
    tables: ['purchase', 'purchase_item', 'purchase_payment', 'purchase_document', 'stock', 'stock_movement', 'low_stock_snapshot'],
  },
  {
    id: 'sales',
    label: 'Sales Documents',
    color: '#1a6fb0',
    tables: [
      'quotation', 'quotation_item',
      'sales_order', 'sales_order_item',
      'delivery_challan', 'delivery_challan_item',
      'invoice', 'invoice_item',
      'credit_note', 'credit_note_item',
      'payment',
    ],
  },
  {
    id: 'commerce',
    label: 'Commerce Extras',
    color: '#a0388a',
    tables: ['coupon', 'coupon_product', 'document_sequence'],
  },
  {
    id: 'projects',
    label: 'Projects & Labour',
    color: '#2e7d32',
    tables: [
      'work_type', 'project', 'project_material', 'project_expense', 'project_payment',
      'worker', 'worker_attendance', 'worker_payment',
    ],
  },
  {
    id: 'finance',
    label: 'Expenses',
    color: '#8d6e63',
    tables: ['expense_category', 'business_expense', 'expense_receipt'],
  },
];

// Everything real that is NOT drawn as a box in the main ERD (it exists,
// but drawing it would make the diagram unreadable or it belongs to a
// separate concern). Listed in full in the appendix instead, grouped here
// the same way so the appendix reads as an extension of the diagram, not a
// disconnected dump.
export const APPENDIX_GROUPS = [
  {
    label: 'Branding & documents (tenant-owned media, stored as bytea)',
    tables: ['tenant_logo', 'tenant_signature', 'tenant_upi_qr', 'tenant_bank_account', 'tenant_bank_account_qr', 'user_avatar'],
  },
  {
    label: 'Notifications & messaging',
    tables: ['notification_log', 'tenant_whatsapp_connection'],
  },
  {
    label: 'Security, audit & compliance',
    tables: ['security_audit_log', 'activity_log', 'password_reset_token', 'user_backup_code', 'user_consent'],
  },
  {
    label: 'Support desk',
    tables: ['support_ticket', 'support_ticket_message'],
  },
  {
    label: 'Cross-tenant Platform Admin console (outside tenant isolation, own auth)',
    tables: [
      'platform_admin', 'platform_admin_backup_code', 'platform_admin_refresh_token', 'platform_audit_log',
      'platform_incident', 'feature_flag', 'job_execution_log',
      'platform_subscription_order', 'platform_subscription_payment', 'platform_tenant_export', 'platform_razorpay_config',
      'subscription_coupon',
    ],
  },
  {
    label: 'Cross-cutting infrastructure',
    tables: ['idempotency_record'],
  },
];
