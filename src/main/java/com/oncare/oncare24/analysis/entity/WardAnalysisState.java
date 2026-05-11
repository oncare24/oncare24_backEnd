package com.oncare.oncare24.analysis.entity;

import com.oncare.oncare24.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "ward_analysis_state",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ward_analysis_state_type", columnNames = {"ward_id", "analysis_type"})
        },
        indexes = {
                @Index(name = "idx_ward_analysis_state_ward", columnList = "ward_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WardAnalysisState extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ward_id", nullable = false)
    private Long wardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type", nullable = false, length = 30)
    private AnalysisType analysisType;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(name = "analyzed_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime analyzedAt;

    @Builder
    private WardAnalysisState(
            Long wardId,
            AnalysisType analysisType,
            int statusCode,
            LocalDateTime analyzedAt
    ) {
        this.wardId = wardId;
        this.analysisType = analysisType;
        this.statusCode = statusCode;
        this.analyzedAt = analyzedAt;
    }

    public void updateStatus(int statusCode, LocalDateTime analyzedAt) {
        this.statusCode = statusCode;
        this.analyzedAt = analyzedAt;
    }
}
