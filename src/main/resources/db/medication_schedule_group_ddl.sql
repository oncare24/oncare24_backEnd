-- 봉지(DoseGroup) 모델 도입: group_id, source 컬럼 추가
-- dev(ddl-auto=update)는 컬럼은 자동 생성하지만 인덱스는 누락될 수 있어 적용 권장.
-- prod(ddl-auto=validate)는 배포 전 반드시 적용.
ALTER TABLE medication_schedule
    ADD COLUMN group_id VARCHAR(100) NULL,
    ADD COLUMN source   VARCHAR(10)  NULL;

CREATE INDEX idx_med_schedule_ward_group ON medication_schedule (ward_id, group_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- 백필 전략 (별도 실행. 조회 경로는 group_id NULL이면 'legacy:<schedule_id>'로
-- 노출하므로 백필 없이도 동작은 한다. 정식 봉지 묶음이 필요할 때 수행.)
--
-- source 보정 (평문만으로 가능):
--   UPDATE medication_schedule
--      SET source = CASE WHEN codef_key_bidx IS NOT NULL THEN 'AUTO' ELSE 'MANUAL' END
--    WHERE source IS NULL;
--
-- group_id 보정:
--   - AUTO(처방): 같은 처방(prescribeNo) 단위로 묶어야 하나 prescribeNo는 평문에 없음
--     (codef_key_bidx = HMAC(prescribeNo_drugCode)는 약 단위). 처방 단위 묶음이 필요하면
--     encrypted_activity_log payload를 복호화하는 애플리케이션 배치로 수행 권장.
--     임시(약 단위) 묶음만 필요하면: group_id = CONCAT('codef:bidx:', codef_key_bidx).
--   - MANUAL(수동): 같은 (ward_id, 약명, 유형, 기간)을 한 group으로. 약명이 암호화되어
--     있으므로 역시 복호화 배치 권장. 미수행 시 'legacy:<schedule_id>'로 개별 노출됨.
-- ─────────────────────────────────────────────────────────────────────────────
