# Sample import data

Test data for the two file-upload/import features that exist in the app today
(confirmed against the code before generating this — see below). Each has
100 rows of realistic hardware-shop data and is provided in both formats the
importer accepts (CSV and Excel `.xlsx`), so you can test either upload path.

**Only these two pages have a file-upload/import option.** Customer,
Supplier, Inventory, Category, Brand, etc. have no import screen in the
current codebase — there was nothing to generate sample files for there.

| File | Used on | Columns |
|---|---|---|
| `product-import-sample.csv` / `.xlsx` | Product List page → **Import products** dialog | Product Name, Product Code, Category, Brand, Unit, HSN Code, GST %, Purchase Price, Selling Price, MRP, Minimum Stock, Reorder Level |
| `purchase-bill-import-sample.csv` / `.xlsx` | Purchase List/Detail page → **Import supplier bill** dialog | Product Name, Brand, Category, SKU, Quantity, Unit, Unit Price, GST % |

## Notes on the data

- **Product import**: all 100 rows are new products across 10 categories
  (Hand Tools, Power Tools, Fasteners, Plumbing, Electrical, Paint, Adhesives
  & Sealants, Sanitary Ware, Hinges & Fittings, Locks & Security), each with a
  unique `Product Code` (`HW-001`…`HW-100`) so every row creates a new
  product on import.
- **Purchase bill import**: same 100 product names, but about a third carry a
  `SKU` (treated as new products) and the rest leave `SKU` blank (matched by
  name against the catalog — import them *after* the product file, or expect
  those rows to show as "New" instead of "Existing"). Quantities, unit
  prices and GST% are randomized but realistic.
- Required columns per the backend parser: Product Name, Unit, GST %,
  Purchase Price, Selling Price, MRP for the product importer; Product Name,
  Quantity, Unit Price for the purchase-bill importer. Everything else is
  optional and left blank on some real bills, so these samples fill every
  column to exercise the full column set.

Regenerated with `docs/sample-import-data/gen.js` (Node + the `xlsx` npm
package) — rerun it with different seeds if you need a fresh batch.
