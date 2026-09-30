import { BASE, suite, withBrowser, newPage, envelope, hasHorizontalScroll } from '../support/harness.mjs';
import { signedInApi, UNPAID_INVOICE } from '../support/fixtures.mjs';

/**
 * Consolidation audit (2026-09-22): every required viewport against every
 * major screen, in the production bundle with the API stubbed. Two
 * assertions per (viewport, screen): no horizontal document overflow, and
 * the page rendered something (its main landmark has height) without a
 * page error. Breakpoint edges (639/640, 767/768, 1023/1024) are included
 * because a layout that is right at 640 and wrong at 639 is the usual bug.
 */
const VIEWPORTS = [
  ['360x640', 360, 640, true], ['360x800', 360, 800, true], ['375x812', 375, 812, true],
  ['390x844', 390, 844, true], ['393x852', 393, 852, true], ['412x915', 412, 915, true],
  ['430x932', 430, 932, true],
  ['639x900', 639, 900, true], ['640x900', 640, 900, true], ['767x1024', 767, 1024, true], ['768x1024', 768, 1024, true],
  ['820x1180', 820, 1180, true], ['1023x768', 1023, 768, false], ['1024x1366', 1024, 1366, false],
  ['1280x720', 1280, 720, false], ['1366x768', 1366, 768, false], ['1440x900', 1440, 900, false], ['1920x1080', 1920, 1080, false],
];

const LONG = 'Ashirvad CPVC Pipe 3/4 inch SDR 11 Hot & Cold Water Plumbing 3 Metre Length Extra Long Product Name';

const branches = [
  { id: 1, branchCode: 'MAIN', branchName: 'Main Shop', main: true, status: 'ACTIVE', city: 'Madurai', createdAt: '2026-09-01T10:00:00' },
  { id: 2, branchCode: 'GODOWN', branchName: 'Godown ' + LONG, main: false, status: 'ACTIVE', city: 'Madurai', createdAt: '2026-09-01T10:00:00' },
];

function api(url) {
  if (url.includes('/v1/branches/summary')) return envelope(branches.map((b) => ({ branchId: b.id, branchCode: b.branchCode, branchName: b.branchName, main: b.main, invoiceCount: 12, salesPaise: 1234500, salesDisplay: '12,345.00', purchaseCount: 3, purchasesPaise: 500000, purchasesDisplay: '5,000.00', userCount: 2, productsInStock: 40 })));
  if (url.includes('/v1/branches/stock')) return envelope([{ branchId: 1, branchName: 'Main Shop', productId: 1, productCode: 'PRD-1', productName: LONG, unit: 'PCS', quantityOnHand: 12 }, { branchId: 2, branchName: 'Godown', productId: 1, productCode: 'PRD-1', productName: LONG, unit: 'PCS', quantityOnHand: -3 }]);
  if (url.includes('/v1/branches/transfers')) return envelope({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });
  if (url.includes('/v1/branches')) return envelope(branches);
  if (url.includes('/v1/insights/')) return envelope({ window: { from: '2026-06-24', to: '2026-09-22', days: 90 }, current: { from: '2026-08-24', to: '2026-09-22', days: 30 }, previous: { from: '2026-07-25', to: '2026-08-23', days: 30 }, coverThresholdDays: 120, leadTimeDays: 7, lowMarginThresholdPercent: 10, items: [{ productId: 1, productCode: 'PRD-1', productName: LONG, unit: 'PCS', quantityOnHand: 40, quantitySold: 0, lastSoldOn: null, stockValuePaise: 100000, stockValueDisplay: '1,000.00', averageDailySales: 0.5, daysOfCover: 80, reorderLevel: 5, suggestedQuantity: 10, reason: 'At or below its reorder level of 5', sellingPricePaise: 5000, sellingPriceDisplay: '50.00', averageCostPaise: 6000, averageCostDisplay: '60.00', marginPercent: -20, averageRealisedPaise: 4500, averageRealisedDisplay: '45.00', flag: 'SELLING_BELOW_COST' }], rising: [], falling: [], pairs: [], summary: 'One product in stock did not sell in the last 90 days.' });
  if (url.includes('/ledger/balance')) return envelope({ customerId: 3, customerName: 'Ramesh Traders', balancePaise: 700000, balanceDisplay: '7,000.00', totalDebitPaise: 1000000, totalDebitDisplay: '10,000.00', totalCreditPaise: 300000, totalCreditDisplay: '3,000.00' });
  if (url.includes('/ledger/statement')) return envelope({ customerId: 3, customerName: 'Ramesh Traders', from: '2026-06-24', to: '2026-09-22', openingBalancePaise: 0, openingBalanceDisplay: '0.00', entries: [{ id: 1, entryType: 'INVOICE', entryDate: '2026-09-20T10:00:00', debitPaise: 1000000, debitDisplay: '10,000.00', creditPaise: 0, creditDisplay: '0.00', balancePaise: 1000000, balanceDisplay: '10,000.00', referenceType: 'INVOICE', referenceId: 20, referenceNumber: 'INV-000020', notes: LONG }], closingBalancePaise: 700000, closingBalanceDisplay: '7,000.00' });
  if (url.includes('/ledger/ageing')) return envelope({ customerId: 3, customerName: 'Ramesh Traders', asOf: '2026-09-22', buckets: [{ label: '0-30 days', fromDays: 0, toDays: 30, paise: 700000, display: '7,000.00', invoiceCount: 1 }], invoices: [], totalOutstandingPaise: 700000, totalOutstandingDisplay: '7,000.00' });
  if (/\/v1\/customers\/3\/(invoices|quotations|products|summary|credit-check)/.test(url)) return url.includes('/summary') || url.includes('/credit-check') ? envelope({ totalInvoicedDisplay: '0.00', totalPaidDisplay: '0.00', outstandingDisplay: '0.00', invoiceCount: 0 }) : (url.includes('/products') ? envelope([]) : envelope({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }));
  if (url.includes('/v1/customers/3')) return envelope({ id: 3, customerCode: 'CUS-0001', customerName: 'Ramesh Traders ' + LONG, mobileNo: '9876500001', status: 'ACTIVE', whatsappOptIn: true, createdAt: '2026-09-01T10:00:00' });
  if (url.includes('/v1/analytics/profit')) return envelope({ period: {}, revenuePaise: 150000, revenueDisplay: '1,500.00', salesReturnPaise: 0, salesReturnDisplay: '0.00', netRevenuePaise: 150000, netRevenueDisplay: '1,500.00', cogsPaise: 100000, cogsDisplay: '1,000.00', grossProfitPaise: 50000, grossProfitDisplay: '500.00', expensePaise: 0, expenseDisplay: '0.00', netProfitPaise: 50000, netProfitDisplay: '500.00' });
  if (url.includes('/v1/backups')) return envelope([{ id: 1, format: 'JSON', triggerType: 'SCHEDULED', status: 'COMPLETED', recordCount: 120, fileSizeBytes: 40960, createdAt: '2026-09-22T01:30:00' }]);
  if (url.includes('/v1/subscriptions/plans')) return envelope([]);
  if (url.includes('/v1/subscriptions/current')) return envelope(null);
  if (url.includes('/v1/settings/usage')) return envelope({ tier: 'MAX', ownerCount: 1, maxOwners: 3, customerCount: 12, maxCustomers: 1000, supplierCount: 3, maxSuppliers: 200, productCount: 40, maxProducts: 5000 });
  if (/\/v1\/settings\/(whatsapp|brand|bank-accounts)/.test(url)) return url.includes('bank-accounts') ? envelope([]) : envelope({ connected: false, brandName: 'Sara Hardware', themeId: 'emerald' });
  if (url.includes('/v1/tenants/settings') || url.includes('/v1/settings')) return envelope({ id: 1, name: 'Sara Hardware', hasLogo: false, hasSignatureImage: false, hasUpiQrImage: false, subscriptionTier: 'MAX', invoiceTheme: 'CLASSIC', showItemDescription: false, showAlternateUnit: false, tcsEnabled: false, tcsRatePercent: 0, einvoiceEnabled: false });
  return signedInApi()(url);
}

const SCREENS = [
  ['login', '/login'], ['register', '/register'], ['dashboard', '/dashboard'],
  ['products', '/products'], ['customers', '/customers'], ['customer-ledger', '/customers/3'],
  ['invoices', '/invoices'], ['invoice-detail', `/invoices/${UNPAID_INVOICE.id}`], ['invoice-new', '/invoices/new'],
  ['settings', '/settings/shop'], ['branches', '/branches'], ['insights', '/insights'],
  ['profit', '/reports/profit'], ['sync', '/sync'], ['subscription', '/settings/subscription'],
];

export default async function run() {
  const s = suite('responsive viewport sweep');
  await withBrowser(async (browser) => {
  for (const [label, width, height, mobile] of VIEWPORTS) {
    for (const [name, path] of SCREENS) {
      const page = await newPage(browser, { viewport: { width, height }, mobile, api });
      let ok = false, detail = '';
      try {
        await page.goto(BASE + path, { waitUntil: 'networkidle', timeout: 20000 });
        await page.waitForTimeout(250);
        const overflow = await hasHorizontalScroll(page);
        const rendered = await page.evaluate(() => {
          const main = document.querySelector('main') ?? document.body;
          return main.getBoundingClientRect().height > 40 && document.body.innerText.trim().length > 20;
        });
        const scrollW = await page.evaluate(() => document.documentElement.scrollWidth);
        ok = !overflow && rendered && page.__errors.length === 0;
        detail = `${overflow ? `overflow scrollWidth=${scrollW} ` : ''}${rendered ? '' : 'nothing rendered '}${page.__errors.length ? 'errors: ' + page.__errors.slice(0, 2).join(' | ') : ''}`.trim() || 'clean';
      } catch (e) {
        detail = 'failed to load: ' + String(e.message).slice(0, 120);
      }
      s.check(`${label} ${name}`, ok, detail);
      await page.context().close();
    }
  }
});
  return s;
}
