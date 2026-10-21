package com.theclub.clubservice.model;

import com.theclub.common.util.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/** A private proof document uploaded with a club owner application (stored under private/ in the bucket). */
@Entity
@Table(name = "owner_verification_documents")
@Getter
@NoArgsConstructor
public class VerificationDocument {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_id", length = 36, nullable = false)
    private UUID requestId;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uploaded_by", length = 36, nullable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public VerificationDocument(UUID requestId, String fileName, String contentType, long sizeBytes, String storageKey,
                                UUID uploadedBy, LocalDateTime createdAt) {
        this.id = UuidGenerator.generate();
        this.requestId = requestId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.uploadedBy = uploadedBy;
        this.createdAt = createdAt;
    }
}
