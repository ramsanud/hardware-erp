-- =====================================================================
-- MODULE 2 SEED : SUPPLIERS  --  DEVELOPMENT / TEST ONLY
--
-- Lives in classpath:db/seed, which only the dev and test profiles include.
-- application-prod.yml lists classpath:db/migration alone (CR-009).
--
-- Twelve real-shaped suppliers from the Madurai / Sivakasi / Coimbatore
-- hardware trade, so search, filtering by city and pagination all have
-- something to work on.
--
-- GST numbers are structurally valid but fictional. State code 33 is Tamil
-- Nadu, 29 Karnataka, 32 Kerala - matching each address, because the service
-- layer rejects a mismatch.
-- =====================================================================

-- tenant_id = 1: the default tenant seeded by V6.
INSERT INTO supplier
 (tenant_id, supplier_code, supplier_name, contact_person, mobile_no, alternate_mobile_no,
  email, gst_no, pan_no, address_line1, city, state_code, pincode,
  payment_terms_days, credit_limit_paise, bank_account_name, bank_account_no,
  bank_ifsc, bank_name, status, remarks, created_at, created_by, version)
VALUES
(1,'SUP-0001','Sri Balaji Hardware Agencies','Ramesh Kumar','9842011223','9842011224',
 'sales@sribalajihardware.in','33AABCS1429B1ZP','AABCS1429B',
 '144 Big Bazaar Street','Madurai','33','625001',
 30, 50000000,'Sri Balaji Hardware Agencies','50100234567890','HDFC0001234','HDFC Bank',
 'ACTIVE','Reliable for locks and door closers. Slow on mica sheets.',CURRENT_TIMESTAMP,1,0),

(1,'SUP-0002','Kumaran Steel & Fittings','Anbu Selvan','9843122334',NULL,
 'kumaransteel@gmail.com','33AACCK7821M1ZR','AACCK7821M',
 '27 Netaji Road','Madurai','33','625009',
 45, 75000000,'Kumaran Steel And Fittings','20100987654321','ICIC0002345','ICICI Bank',
 'ACTIVE','Best rates on SS 304 items. Insists on 45-day terms.',CURRENT_TIMESTAMP,1,0),

(1,'SUP-0003','Velavan Traders','Muthu Velavan','9894233445','9894233446',
 'velavantraders@yahoo.in','33AAFCV3312K1ZQ','AAFCV3312K',
 '9 Kamarajar Salai','Sivakasi','33','626123',
 15, 25000000,'Velavan Traders','30200456789012','SBIN0003456','State Bank of India',
 'ACTIVE','Screws, nails and fasteners. Delivers twice a week.',CURRENT_TIMESTAMP,1,0),

(1,'SUP-0004','Hettich India Distributors','Priya Narayanan','9840344556',NULL,
 'orders@hettichdist.in','33AAGCH5567L1ZN','AAGCH5567L',
 '212 Anna Nagar Main Road','Chennai','33','600040',
 60, 150000000,'Hettich India Distributors','40300567890123','AXIS0004567','Axis Bank',
 'ACTIVE','Authorised distributor. Premium kitchen and furniture fittings.',CURRENT_TIMESTAMP,1,0),

(1,'SUP-0005','Coimbatore Aluminium Works','Saravana Kumar','9865455667','9865455668',
 'cbealuminium@gmail.com','33AAHCC8890P1ZM','AAHCC8890P',
 '55 Trichy Road','Coimbatore','33','641018',
 30, 60000000,'Coimbatore Aluminium Works','50400678901234','HDFC0005678','HDFC Bank',
 'ACTIVE','Aluminium sections and glass fittings. Freight extra.',CURRENT_TIMESTAMP,1,0),

(1,'SUP-0006','Godrej Locking Solutions - South','Vinod Menon','9880566778',NULL,
 'south.trade@godrejlocks.example','29AABCG1122N1ZT','AABCG1122N',
 '18 Hosur Road','Bengaluru','29','560029',
 45, 200000000,'Godrej Locking Solutions','60500789012345','KKBK0006789','Kotak Mahindra Bank',
 'ACTIVE','Inter-state supply, so purchases attract IGST.',CURRENT_TIMESTAMP,1,0),

(1,'SUP-0007','Ebco Hardware Depot','Farhan Sheikh','9820677889',NULL,
 'depot.chennai@ebcohardware.example','33AABCE4455Q1ZL','AABCE4455Q',
 '78 GST Road','Chennai','33','600045',
 30, 90000000,'Ebco Hardware Depot','70600890123456','UTIB0007890','Axis Bank',
 'ACTIVE','Drawer slides and hinges. Minimum order Rs 25,000.',CURRENT_TIMESTAMP,1,0),

(1,'SUP-0008','Meenakshi Mica & Laminates','Lakshmi Devi','9842788990','9842788991',
 'meenakshimica@rediffmail.com','33AACCM2233R1ZK','AACCM2233R',
 '31 South Masi Street','Madurai','33','625001',
 21, 40000000,'Meenakshi Mica And Laminates','80700901234567','IOBA0008901','Indian Overseas Bank',
 'ACTIVE','Mica sheets and decorative laminates. Sold per sheet.',CURRENT_TIMESTAMP,1,0),

(1,'SUP-0009','Trichy Bathroom Fittings Co','Ashok Raman','9894899001',NULL,
 'trichybath@gmail.com','33AAECT6677S1ZJ','AAECT6677S',
 '102 Thillai Nagar','Tiruchirappalli','33','620018',
 30, 35000000,'Trichy Bathroom Fittings Co','90800012345678','CNRB0009012','Canara Bank',
 'ACTIVE','Bathroom and sanitary hardware.',CURRENT_TIMESTAMP,1,0),

(1,'SUP-0010','Kerala Plywood & Hardware','Joseph Mathew','9847900112',NULL,
 'keralaplywood@gmail.com','32AAFCK8899T1ZH','AAFCK8899T',
 '14 MG Road','Kochi','32','682035',
 30, 45000000,'Kerala Plywood And Hardware','10900123456789','FDRL0010123','Federal Bank',
 'INACTIVE','Stopped trading in 2025. Kept for purchase history.',CURRENT_TIMESTAMP,1,0),

(1,'SUP-0011','Perfect Tools & Ladders','Imran Ali','9840011223',NULL,
 NULL,NULL,NULL,
 '66 Mount Road','Chennai','33','600002',
 0, 0,NULL,NULL,NULL,NULL,
 'ACTIVE','Cash purchases only. No GST registration.',CURRENT_TIMESTAMP,1,0),

(1,'SUP-0012','Anand Fasteners','Deepak Anand','9845122334',NULL,
 'anandfasteners@gmail.com','33AAKCA3344U1ZG','AAKCA3344U',
 '5 Industrial Estate','Madurai','33','625016',
 15, 20000000,'Anand Fasteners','21000234567890','BARB0011234','Bank of Baroda',
 'BLOCKED','Blocked after three short-supplied consignments. Dispute pending.',CURRENT_TIMESTAMP,1,0);

-- A soft-deleted supplier, so the list screen can be checked for exclusion.
INSERT INTO supplier
 (tenant_id, supplier_code, supplier_name, mobile_no, city, state_code,
  payment_terms_days, credit_limit_paise, status, remarks,
  created_at, created_by, deleted_at, deleted_by, version)
VALUES
(1,'SUP-0013','Old Ganesh Hardware (closed)','9840199887','Madurai','33',
 0, 0,'INACTIVE','Shop closed permanently.',
 CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,1,0);

-- ---------------------------------------------------------------------
-- Contacts. Several suppliers have more than one person, which is the whole
-- reason supplier_contact exists as a separate table.
-- ---------------------------------------------------------------------
INSERT INTO supplier_contact
 (supplier_id, contact_name, designation, mobile_no, email, is_primary, created_at, created_by, version)
SELECT s.supplier_id, v.contact_name, v.designation, v.mobile_no, v.email, v.is_primary,
       CURRENT_TIMESTAMP, 1, 0
FROM (VALUES
 ('SUP-0001','Ramesh Kumar','Proprietor','9842011223','ramesh@sribalajihardware.in',TRUE),
 ('SUP-0001','Suresh Kumar','Accounts','9842011225','accounts@sribalajihardware.in',FALSE),
 ('SUP-0002','Anbu Selvan','Sales Manager','9843122334',NULL,TRUE),
 ('SUP-0002','Karthik R','Dispatch','9843122335',NULL,FALSE),
 ('SUP-0004','Priya Narayanan','Regional Manager','9840344556','priya@hettichdist.in',TRUE),
 ('SUP-0004','Ganesh Iyer','Technical Support','9840344557',NULL,FALSE),
 ('SUP-0004','Meera Krishnan','Accounts','9840344558',NULL,FALSE),
 ('SUP-0005','Saravana Kumar','Proprietor','9865455667',NULL,TRUE),
 ('SUP-0006','Vinod Menon','Area Sales Manager','9880566778',NULL,TRUE),
 ('SUP-0007','Farhan Sheikh','Depot Incharge','9820677889',NULL,TRUE),
 ('SUP-0008','Lakshmi Devi','Proprietor','9842788990',NULL,TRUE),
 ('SUP-0009','Ashok Raman','Partner','9894899001',NULL,TRUE),
 ('SUP-0012','Deepak Anand','Proprietor','9845122334',NULL,TRUE)
) AS v(supplier_code, contact_name, designation, mobile_no, email, is_primary)
JOIN supplier s ON s.supplier_code = v.supplier_code;

-- ---------------------------------------------------------------------
-- Activity history, so the supplier history view has something to show.
-- ---------------------------------------------------------------------
INSERT INTO activity_log
 (module_code, entity_type, entity_id, entity_label, action,
  old_values, new_values, user_id, full_name, role_code, ip_address, created_at)
SELECT 'SUPPLIER','SUPPLIER', s.supplier_id, s.supplier_name,'CREATE',
       NULL,
       jsonb_build_object('supplierCode', s.supplier_code,
                          'supplierName', s.supplier_name,
                          'paymentTermsDays', s.payment_terms_days),
       1,'Saravanan Murugan','OWNER','192.168.1.10',
       CURRENT_TIMESTAMP - INTERVAL '20 days'
FROM supplier s WHERE s.supplier_code IN ('SUP-0001','SUP-0002','SUP-0003','SUP-0004');

INSERT INTO activity_log
 (module_code, entity_type, entity_id, entity_label, action,
  old_values, new_values, user_id, full_name, role_code, ip_address, remarks, created_at)
SELECT 'SUPPLIER','SUPPLIER', s.supplier_id, s.supplier_name,'UPDATE',
       jsonb_build_object('paymentTermsDays', 30, 'creditLimitPaise', 50000000),
       jsonb_build_object('paymentTermsDays', 45, 'creditLimitPaise', 75000000),
       3,'Prakash Venkatesan','MANAGER','192.168.1.31',
       'Terms renegotiated after the annual review.',
       CURRENT_TIMESTAMP - INTERVAL '6 days'
FROM supplier s WHERE s.supplier_code = 'SUP-0002';

INSERT INTO activity_log
 (module_code, entity_type, entity_id, entity_label, action,
  user_id, full_name, role_code, ip_address, remarks, created_at)
SELECT 'SUPPLIER','SUPPLIER', s.supplier_id, s.supplier_name,'STATUS_CHANGE',
       1,'Saravanan Murugan','OWNER','192.168.1.10',
       'Blocked after three short-supplied consignments.',
       CURRENT_TIMESTAMP - INTERVAL '2 days'
FROM supplier s WHERE s.supplier_code = 'SUP-0012';
