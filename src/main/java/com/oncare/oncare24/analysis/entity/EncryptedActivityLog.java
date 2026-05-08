package com.oncare.oncare24.analysis.entity;

import com.oncare.oncare24.global.common.BaseTimeEntity;
import com.oncare.oncare24.user.entity.User;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "encrypted_activity_log",
        indexes = {
                @Index(name = "idx_enc_activity_ward_occurred", columnList = "ward_id, occurred_at DESC"),
                @Index(name = "idx_enc_activity_event_type", columnList = "event_type, occurred_at DESC"),
                @Index(name = "idx_enc_activity_source", columnList = "source_table, source_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EncryptedActivityLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ward_id", nullable = false)
    private Long wardId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "ward_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_encrypted_activity_log_ward")
    )
    private User ward;

    @Column(name = "data_key_id", nullable = false, length = 100)
    private String dataKeyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private ActivityEventType eventType;

    @Column(name = "source_table", length = 100)
    private String sourceTable;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "occurred_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime occurredAt;

    @Column(name = "nonce", nullable = false, columnDefinition = "VARBINARY(12)")
    private byte[] nonce;

    @Column(name = "auth_tag", nullable = false, columnDefinition = "VARBINARY(16)")
    private byte[] authTag;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "ciphertext", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] ciphertext;

    @Lob
    @Column(name = "aad_json", columnDefinition = "TEXT")
    private String aadJson;

    @Builder
    private EncryptedActivityLog(
            Long wardId,
            String dataKeyId,
            ActivityEventType eventType,
            String sourceTable,
            Long sourceId,
            LocalDateTime occurredAt,
            byte[] nonce,
            byte[] authTag,
            byte[] ciphertext,
            String aadJson
    ) {
        this.wardId = wardId;
        this.dataKeyId = dataKeyId;
        this.eventType = eventType;
        this.sourceTable = sourceTable;
        this.sourceId = sourceId;
        this.occurredAt = occurredAt;
        this.nonce = nonce;
        this.authTag = authTag;
        this.ciphertext = ciphertext;
        this.aadJson = aadJson;
    }
}
