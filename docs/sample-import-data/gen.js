const fs = require('fs');
const path = require('path');
const XLSX = require('xlsx');

const OUT = path.join(__dirname, 'out');
fs.mkdirSync(OUT, { recursive: true });

// ---- Hardware-shop reference data (India) ----------------------------------

const categories = [
  'Hand Tools', 'Power Tools', 'Fasteners', 'Plumbing', 'Electrical',
  'Paint', 'Adhesives & Sealants', 'Sanitary Ware', 'Hinges & Fittings',
  'Locks & Security', 'Safety Equipment', 'Pipes & Fittings',
  'Cement & Building Material', 'Wires & Cables', 'Abrasives',
];

const brands = [
  'Stanley', 'Taparia', 'Bosch', 'Havells', 'Anchor by Panasonic',
  'Asian Paints', 'Berger', 'Pidilite', 'Supreme Industries', 'Finolex',
  'Jindal', 'Tata Steel', 'Ambuja Cement', 'UltraTech', 'Godrej Locks',
  'Hettich', 'Polycab', 'Crompton', 'Cera', 'Hindware',
];

const unitsByCategory = {
  'Hand Tools': ['PCS', 'SET'],
  'Power Tools': ['PCS', 'SET'],
  'Fasteners': ['BOX', 'KG', 'PCS'],
  'Plumbing': ['PCS', 'MTR'],
  'Electrical': ['PCS', 'ROLL', 'BOX'],
  'Paint': ['LTR', 'KG'],
  'Adhesives & Sealants': ['PCS', 'KG'],
  'Sanitary Ware': ['PCS', 'SET'],
  'Hinges & Fittings': ['PCS', 'BOX', 'DOZEN'],
  'Locks & Security': ['PCS', 'SET'],
  'Safety Equipment': ['PCS', 'SET', 'BOX'],
  'Pipes & Fittings': ['MTR', 'PCS'],
  'Cement & Building Material': ['BAG', 'KG'],
  'Wires & Cables': ['ROLL', 'MTR'],
  'Abrasives': ['PCS', 'BOX'],
};

const productNamesByCategory = {
  'Hand Tools': ['Claw Hammer', 'Screwdriver Set', 'Adjustable Wrench', 'Combination Plier', 'Hacksaw Blade', 'Measuring Tape 5m', 'Spirit Level 24"', 'Hex Key Set', 'Chisel Set', 'Wire Cutter'],
  'Power Tools': ['Impact Drill Machine', 'Angle Grinder 4"', 'Jigsaw Machine', 'Rotary Hammer Drill', 'Bench Grinder', 'Circular Saw', 'Heat Gun', 'Cordless Screwdriver', 'Orbital Sander', 'Electric Planer'],
  'Fasteners': ['MS Bolt M8x50', 'MS Nut M8', 'Wood Screw 1.5"', 'Self Tapping Screw', 'Anchor Fastener 10mm', 'Washer M8', 'Machine Screw M6', 'Carriage Bolt M10', 'Rivet 4mm', 'Threaded Rod M10'],
  'Plumbing': ['PVC Ball Valve 1"', 'Brass Gate Valve 1/2"', 'PTFE Tape', 'Pipe Wrench 14"', 'Flexible Hose Pipe', 'Bib Cock', 'PVC Elbow 1"', 'Angle Valve', 'Water Filter Cartridge', 'Foot Valve 1"'],
  'Electrical': ['Modular Switch 6A', 'MCB 32A Single Pole', 'LED Bulb 9W', 'Ceiling Fan Regulator', 'Extension Board 4-Socket', 'Electrical Tape', 'Two Pin Socket', 'Distribution Board 8-Way', 'Tube Light 20W', 'Doorbell Switch'],
  'Paint': ['Emulsion Paint White', 'Enamel Paint Blue', 'Wood Primer', 'Wall Putty', 'Distemper Paint', 'Metal Primer Red Oxide', 'Textured Paint', 'Waterproof Paint', 'Spray Paint Black', 'Anti-Rust Paint'],
  'Adhesives & Sealants': ['Fevicol SH Adhesive', 'Silicone Sealant Clear', 'Epoxy Adhesive', 'Araldite Standard', 'PVC Pipe Solvent Cement', 'M-Seal Epoxy Putty', 'Fevi Kwik Instant Adhesive', 'Tile Adhesive', 'Foam Sealant Spray', 'Rubber Gasket Adhesive'],
  'Sanitary Ware': ['Wash Basin White', 'Health Faucet', 'Shower Head', 'Toilet Seat Cover', 'Kitchen Sink Single Bowl', 'Basin Mixer Tap', 'Overhead Shower Arm', 'Urinal Flush Valve', 'Soap Dispenser', 'Towel Rod'],
  'Hinges & Fittings': ['Door Hinge 4"', 'Cabinet Hinge Soft Close', 'Drawer Channel 18"', 'Furniture Handle', 'Door Stopper', 'Sliding Door Roller', 'Tower Bolt 6"', 'Piano Hinge 6ft', 'Cabinet Lock Catch', 'Curtain Bracket'],
  'Locks & Security': ['Mortise Lock Set', 'Padlock 50mm', 'Door Latch', 'Digital Door Lock', 'Cupboard Lock', 'Hasp & Staple', 'Chain Lock', 'Night Latch', 'Drawer Lock', 'Gate Latch'],
  'Safety Equipment': ['Safety Helmet', 'Safety Gloves Leather', 'Safety Goggles', 'Ear Plugs', 'Reflective Safety Vest', 'Dust Mask N95', 'Safety Shoes', 'Fire Extinguisher 2kg', 'First Aid Kit', 'Safety Harness'],
  'Pipes & Fittings': ['PVC Pipe 1" (3m)', 'CPVC Pipe 3/4" (3m)', 'GI Pipe 1" (6m)', 'PVC Tee Joint', 'PVC Coupler 1"', 'CPVC Elbow 3/4"', 'PVC Union Joint', 'GI Nipple 1"', 'PVC End Cap', 'CPVC Reducer'],
  'Cement & Building Material': ['OPC Cement 43 Grade', 'PPC Cement', 'White Cement', 'River Sand', 'Waterproofing Compound', 'Tile Grout', 'RCC Chemical Admixture', 'Fly Ash Brick', 'Concrete Block', 'Ready Mix Plaster'],
  'Wires & Cables': ['Copper Wire 1.5 sq mm', 'Copper Wire 2.5 sq mm', 'Armoured Cable 4 sq mm', 'Speaker Wire', 'Coaxial TV Cable', 'Telephone Cable', 'Flexible Wire 4 sq mm', 'Earthing Wire', 'CAT6 Network Cable', 'Submersible Cable'],
  'Abrasives': ['Sand Paper 100 Grit', 'Grinding Wheel 4"', 'Cutting Wheel 4"', 'Wire Brush', 'Emery Cloth Roll', 'Flap Disc 4"', 'Wet & Dry Paper', 'Buffing Pad', 'Diamond Cutting Blade', 'Steel Wool'],
};

const gstRatesCommon = [5, 12, 18, 28];

function pick(arr, i) { return arr[i % arr.length]; }
function rnd(seed) { // simple deterministic pseudo-random 0..1
  const x = Math.sin(seed * 9973 + 12.9898) * 43758.5453;
  return x - Math.floor(x);
}
function rndInt(seed, min, max) { return min + Math.floor(rnd(seed) * (max - min + 1)); }
function money(v) { return Math.round(v * 100) / 100; }
function pad(n, len) { return String(n).padStart(len, '0'); }

// ---- Build 100 product rows -------------------------------------------------

const productRows = [];
let seq = 1;
for (const cat of categories) {
  const names = productNamesByCategory[cat];
  for (let i = 0; i < names.length; i++) {
    if (seq > 100) break;
    const name = names[i];
    const brand = pick(brands, seq * 3 + i);
    const unit = pick(unitsByCategory[cat], i);
    const gst = pick(gstRatesCommon, seq);
    const purchase = money(rndInt(seq, 20, 4000) + rnd(seq) * 10);
    const selling = money(purchase * (1 + rndInt(seq + 1, 12, 35) / 100));
    const mrp = money(selling * (1 + rndInt(seq + 2, 5, 20) / 100));
    const minStock = rndInt(seq + 3, 5, 25);
    const reorder = minStock + rndInt(seq + 4, 5, 15);
    const hsn = String(rndInt(seq + 5, 3200, 8550));
    const code = `HW-${pad(seq, 3)}`;
    productRows.push({
      'Product Name': name,
      'Product Code': code,
      'Category': cat,
      'Brand': brand,
      'Unit': unit,
      'HSN Code': hsn,
      'GST %': gst,
      'Purchase Price': purchase,
      'Selling Price': selling,
      'MRP': mrp,
      'Minimum Stock': minStock,
      'Reorder Level': reorder,
    });
    seq++;
  }
  if (seq > 100) break;
}
// top up to exactly 100 if categories*10 < 100 (15*10=150 so already >=100, trim)
const finalProductRows = productRows.slice(0, 100);

// ---- Build 100 purchase-bill-import rows -----------------------------------
// Mix of existing-looking products (no SKU given, matched by name) and new
// products (with SKU) - mirrors a real supplier bill: some lines re-stock
// products already in the catalog, some introduce new ones.

const purchaseRows = [];
seq = 1;
for (const cat of categories) {
  const names = productNamesByCategory[cat];
  for (let i = 0; i < names.length; i++) {
    if (seq > 100) break;
    const name = names[i];
    const brand = pick(brands, seq * 5 + i);
    const unit = pick(unitsByCategory[cat], i + 1);
    const gst = pick(gstRatesCommon, seq + 2);
    const qty = rndInt(seq, 5, 200);
    const unitPrice = money(rndInt(seq + 1, 20, 3500) + rnd(seq + 1) * 10);
    const isNew = seq % 3 === 0; // roughly a third are new products
    purchaseRows.push({
      'Product Name': name,
      'Brand': brand,
      'Category': cat,
      'SKU': isNew ? `HW-${pad(seq, 3)}` : '',
      'Quantity': qty,
      'Unit': unit,
      'Unit Price': unitPrice,
      'GST %': gst,
    });
    seq++;
  }
  if (seq > 100) break;
}
const finalPurchaseRows = purchaseRows.slice(0, 100);

// ---- Writers -----------------------------------------------------------

function toCsv(rows, headers) {
  const esc = (v) => {
    const s = String(v);
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const lines = [headers.join(',')];
  for (const row of rows) lines.push(headers.map((h) => esc(row[h])).join(','));
  return lines.join('\r\n') + '\r\n';
}

function toXlsx(rows, headers, sheetName, filePath) {
  const ws = XLSX.utils.json_to_sheet(rows, { header: headers });
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, sheetName);
  XLSX.writeFile(wb, filePath);
}

const productHeaders = ['Product Name', 'Product Code', 'Category', 'Brand', 'Unit', 'HSN Code', 'GST %', 'Purchase Price', 'Selling Price', 'MRP', 'Minimum Stock', 'Reorder Level'];
const purchaseHeaders = ['Product Name', 'Brand', 'Category', 'SKU', 'Quantity', 'Unit', 'Unit Price', 'GST %'];

fs.writeFileSync(path.join(OUT, 'product-import-sample.csv'), toCsv(finalProductRows, productHeaders));
toXlsx(finalPurchaseRows, purchaseHeaders, 'Bill', path.join(OUT, 'purchase-bill-import-sample.xlsx'));

// Also produce the opposite format of each, for convenience.
toXlsx(finalProductRows, productHeaders, 'Products', path.join(OUT, 'product-import-sample.xlsx'));
fs.writeFileSync(path.join(OUT, 'purchase-bill-import-sample.csv'), toCsv(finalPurchaseRows, purchaseHeaders));

console.log('Product rows:', finalProductRows.length);
console.log('Purchase rows:', finalPurchaseRows.length);
console.log('Written to', OUT);
