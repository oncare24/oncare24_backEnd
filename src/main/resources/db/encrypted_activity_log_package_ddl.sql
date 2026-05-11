-- Reference DDL only. This project does not currently use Flyway or Liquibase.
-- Dev profile uses ddl-auto=update, but the current Docker DB may still need this
-- if the app has not been restarted after the entity change.
-- Prod profile uses ddl-auto=validate, so equivalent schema changes must be applied
-- before starting with the prod profile.

-- If encrypted_activity_log already has rows, add the new column as nullable first,
-- backfill/re-encrypt rows into full FFI packages, then make it NOT NULL.
ALTER TABLE encrypted_activity_log
    ADD COLUMN encrypted_package LONGBLOB NULL;

-- MySQL enum column must include newly added source-event types if the table
-- was created before MEDICATION_EVENT, LOCATION_EVENT, and DEVICE_EVENT existed.
ALTER TABLE encrypted_activity_log
    MODIFY COLUMN event_type ENUM(
        'MEDICATION_ANALYSIS',
        'LOCATION_ANALYSIS',
        'DEVICE_ANALYSIS',
        'MEDICATION_EVENT',
        'LOCATION_EVENT',
        'DEVICE_EVENT'
    ) NOT NULL;

-- After existing encrypted rows have been migrated/re-encrypted or discarded:
ALTER TABLE encrypted_activity_log
    MODIFY COLUMN encrypted_package LONGBLOB NOT NULL;

ALTER TABLE encrypted_activity_log
    DROP COLUMN nonce,
    DROP COLUMN auth_tag,
    DROP COLUMN ciphertext;

-- Phase 1 medication source encryption-only changes.
-- medication_log keeps only lookup metadata. Sensitive source values move into
-- encrypted_activity_log.encrypted_package with source_table='medication_log'.
ALTER TABLE medication_log
    ADD COLUMN encrypted_activity_log_id BIGINT NULL,
    MODIFY COLUMN taken_at DATETIME(6) NULL,
    MODIFY COLUMN medication_name VARCHAR(100) NULL,
    MODIFY COLUMN log_source VARCHAR(30) NULL;

-- medication_schedule keeps id/ward/is_active/timestamps as plaintext metadata.
-- Schedule details used by analysis move into encrypted_activity_log.encrypted_package
-- with source_table='medication_schedule'.
ALTER TABLE medication_schedule
    ADD COLUMN encrypted_activity_log_id BIGINT NULL,
    MODIFY COLUMN medication_name VARCHAR(100) NULL,
    MODIFY COLUMN scheduled_time TIME NULL,
    MODIFY COLUMN allowed_early_minutes INT NULL,
    MODIFY COLUMN allowed_delay_minutes INT NULL,
    MODIFY COLUMN schedule_type VARCHAR(20) NULL,
    MODIFY COLUMN day_of_week VARCHAR(20) NULL;

-- Latest app-display analysis state cache only. This table must not store
-- analysis details, decrypted payloads, coordinates, medication names, or reasons.
CREATE TABLE ward_analysis_state (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ward_id BIGINT NOT NULL,
    analysis_type VARCHAR(30) NOT NULL,
    status_code INT NOT NULL,
    analyzed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ward_analysis_state_type UNIQUE (ward_id, analysis_type)
);

CREATE INDEX idx_ward_analysis_state_ward
    ON ward_analysis_state (ward_id);
