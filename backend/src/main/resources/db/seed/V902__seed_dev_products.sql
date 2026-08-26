-- =====================================================================
-- DEV SEED : PRODUCTS
-- Never loaded in production - see the note at the top of V900. Twelve
-- common hardware-shop items with real stock, so an invoice can be created
-- against them immediately without manually adjusting stock first.
--
-- tenant_id = 1: the default tenant seeded by V6 (same as every other seed
-- file). Explicit product_code values are safe alongside auto-generation:
-- ProductRepository.findHighestGeneratedCodeNumber scans for the MAX
-- existing number, it does not rely on a database sequence.
-- =====================================================================

INSERT INTO product
    (tenant_id, product_code, product_name, unit, hsn_code, gst_rate_percent,
     purchase_price_paise, selling_price_paise, mrp_paise,
     minimum_stock, reorder_level, status, created_at, version)
VALUES
 (1, 'PRD-000010', 'Godrej Duplex Lock 70mm', 'PCS', '8301', 18.00,
  35000, 55000, 65000, 5, 10, 'ACTIVE', CURRENT_TIMESTAMP, 0),
 (1, 'PRD-000011', 'Asian Paints Tractor Emulsion 1L (White)', 'PCS', '3209', 18.00,
  22000, 32000, 38000, 10, 20, 'ACTIVE', CURRENT_TIMESTAMP, 0),
 (1, 'PRD-000012', 'Anchor 6A Modular Switch', 'PCS', '8536', 18.00,
  4000, 6500, 8000, 20, 40, 'ACTIVE', CURRENT_TIMESTAMP, 0),
 (1, 'PRD-000013', 'Finolex 1.5 sq mm Copper Wire (90m coil)', 'PCS', '8544', 18.00,
  180000, 235000, 260000, 3, 5, 'ACTIVE', CURRENT_TIMESTAMP, 0),
 (1, 'PRD-000014', 'Stanley Claw Hammer 450g', 'PCS', '8205', 18.00,
  28000, 42000, 50000, 5, 10, 'ACTIVE', CURRENT_TIMESTAMP, 0),
 (1, 'PRD-000015', 'M8 x 50mm Hex Bolt (box of 100)', 'BOX', '7318', 18.00,
  35000, 48000, 55000, 8, 15, 'ACTIVE', CURRENT_TIMESTAMP, 0),
 (1, 'PRD-000016', 'PVC Pipe 1 inch (3m length)', 'PCS', '3917', 18.00,
  12000, 18000, 21000, 15, 25, 'ACTIVE', CURRENT_TIMESTAMP, 0),
 (1, 'PRD-000017', 'Fevicol SR 998 Adhesive 500g', 'PCS', '3506', 18.00,
  9000, 14000, 16500, 10, 20, 'ACTIVE', CURRENT_TIMESTAMP, 0),
 (1, 'PRD-000018', 'Bosch GSB 500W Impact Drill Machine', 'PCS', '8467', 18.00,
  185000, 249000, 279000, 2, 4, 'ACTIVE', CURRENT_TIMESTAMP, 0),
 (1, 'PRD-000019', 'Cera Wash Basin Tap (Chrome)', 'PCS', '8481', 18.00,
  65000, 89000, 99000, 4, 8, 'ACTIVE', CURRENT_TIMESTAMP, 0),
 (1, 'PRD-000020', 'Nippon 8 inch Paint Brush', 'PCS', '9603', 12.00,
  4500, 7000, 8500, 15, 30, 'ACTIVE', CURRENT_TIMESTAMP, 0),
 (1, 'PRD-000021', 'GI Binding Wire 1kg Coil', 'PCS', '7217', 12.00,
  9500, 13500, 15500, 10, 20, 'ACTIVE', CURRENT_TIMESTAMP, 0);

-- Real opening stock for every product above, so a sale can be made
-- immediately - not a Stock row created lazily at zero on first API call.
INSERT INTO stock (tenant_id, product_id, quantity_on_hand, created_at, version)
SELECT 1, product_id,
       CASE product_code
           WHEN 'PRD-000010' THEN 40
           WHEN 'PRD-000011' THEN 60
           WHEN 'PRD-000012' THEN 150
           WHEN 'PRD-000013' THEN 12
           WHEN 'PRD-000014' THEN 35
           WHEN 'PRD-000015' THEN 50
           WHEN 'PRD-000016' THEN 80
           WHEN 'PRD-000017' THEN 45
           WHEN 'PRD-000018' THEN 8
           WHEN 'PRD-000019' THEN 20
           WHEN 'PRD-000020' THEN 60
           WHEN 'PRD-000021' THEN 30
       END,
       CURRENT_TIMESTAMP, 0
FROM product
WHERE tenant_id = 1
  AND product_code IN ('PRD-000010','PRD-000011','PRD-000012','PRD-000013',
                        'PRD-000014','PRD-000015','PRD-000016','PRD-000017',
                        'PRD-000018','PRD-000019','PRD-000020','PRD-000021');

-- The matching opening-stock movement, so the ledger explains where the
-- quantity came from rather than a Stock row appearing with no history.
INSERT INTO stock_movement
    (tenant_id, product_id, movement_type, quantity_change, balance_after,
     reference_type, notes, created_at)
SELECT s.tenant_id, s.product_id, 'INITIAL', s.quantity_on_hand, s.quantity_on_hand,
       'SEED', 'Opening stock (dev seed data)', CURRENT_TIMESTAMP
FROM stock s
JOIN product p ON p.product_id = s.product_id
WHERE p.tenant_id = 1
  AND p.product_code IN ('PRD-000010','PRD-000011','PRD-000012','PRD-000013',
                          'PRD-000014','PRD-000015','PRD-000016','PRD-000017',
                          'PRD-000018','PRD-000019','PRD-000020','PRD-000021');
