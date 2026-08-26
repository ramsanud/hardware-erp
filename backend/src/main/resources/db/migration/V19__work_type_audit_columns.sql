-- BUG fix: WorkType extends BaseEntity (created_by/updated_by, matching
-- every other entity in the codebase), but V18 only gave work_type plain
-- created_at/updated_at columns - caught immediately by ddl-auto:validate
-- refusing to start, exactly the kind of drift that setting exists to
-- catch. V18 already applied (per the hard rule against editing an applied
-- migration), so this adds the missing columns rather than rewriting it.
ALTER TABLE work_type ADD COLUMN created_by BIGINT REFERENCES app_user (user_id);
ALTER TABLE work_type ADD COLUMN updated_by BIGINT REFERENCES app_user (user_id);
