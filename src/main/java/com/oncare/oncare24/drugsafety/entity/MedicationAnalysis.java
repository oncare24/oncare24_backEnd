package com.oncare.oncare24.drugsafety.entity;

import com.oncare.oncare24.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 복약 안전 분석 결과 캐시.
 * <p>
 * Graph RAG 서버에서 받은 Warning 배열을 JSON 문자열로 저장.
 * 한 사용자당 최신 결과 1행만 유지 (덮어쓰기).
 * 분석 시각({@link #analyzedAt})은 인지 UX(예: "30일 전 분석" 배너)에 사용.
 */
@Entity
@Table(
        name = "medication_analysis",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_medication_analysis_user_id",
                        columnNames = "user_id"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicationAnalysis extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "warnings_json", nullable = false, columnDefinition = "JSON")
    private String warningsJson;

    @Column(name = "prescriptions_json", nullable = false, columnDefinition = "JSON")
    private String prescriptionsJson;

    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;

    @Builder
    private MedicationAnalysis(Long userId, String warningsJson, String prescriptionsJson, LocalDateTime analyzedAt) {
        this.userId = userId;
        this.warningsJson = warningsJson;
        this.prescriptionsJson = prescriptionsJson;
        this.analyzedAt = analyzedAt;
    }

    /**
     * 새 분석 결과로 덮어쓰기.
     */
    public void overwrite(String warningsJson, String prescriptionsJson, LocalDateTime analyzedAt) {
        this.warningsJson = warningsJson;
        this.prescriptionsJson = prescriptionsJson;
        this.analyzedAt = analyzedAt;
    }
}