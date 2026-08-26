-- Same class of defect as V4 (BUG-AUTH-011): V2 declared state_code CHAR(2)
-- and pincode CHAR(6), but Supplier.stateCode / Supplier.pincode are mapped
-- with @Column(length = N) and no columnDefinition, which Hibernate resolves
-- to VARCHAR. See BUG-SUP-003 in BUG_REGISTRY.md.
ALTER TABLE supplier ALTER COLUMN state_code TYPE VARCHAR(2);
ALTER TABLE supplier ALTER COLUMN pincode TYPE VARCHAR(6);
