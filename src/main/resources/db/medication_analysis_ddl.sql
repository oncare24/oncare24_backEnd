-- =====================================================================
-- 복약 안전 분석 (Graph RAG) 결과 캐시
-- 한 사용자당 최신 결과 1행만 유지 (UNIQUE on user_id)
-- =====================================================================
CREATE TABLE medication_analysis
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    warnings_json JSON        NOT NULL,
    analyzed_at   DATETIME(6) NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_medication_analysis_user_id UNIQUE (user_id),
    CONSTRAINT fk_medication_analysis_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '복약 안전 분석 결과 캐시 (Graph RAG)';