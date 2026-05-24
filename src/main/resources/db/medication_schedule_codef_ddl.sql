-- 처방 자동 등록: 종료일 + CODEF 블라인드 인덱스(중복 방지)
-- dev(ddl-auto=update)는 컬럼은 자동 생성하지만 인덱스는 누락될 수 있어 적용 권장.
-- prod(ddl-auto=validate)는 배포 전 반드시 적용.
ALTER TABLE medication_schedule
    ADD COLUMN end_date DATE NULL,
    ADD COLUMN codef_key_bidx VARCHAR(64) NULL;

CREATE INDEX idx_med_schedule_ward_codef ON medication_schedule (ward_id, codef_key_bidx);
CREATE INDEX idx_med_schedule_active_end ON medication_schedule (is_active, end_date);