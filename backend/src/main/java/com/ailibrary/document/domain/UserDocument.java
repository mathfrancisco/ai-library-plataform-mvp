package com.ailibrary.document.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class UserDocument {
    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "book_id")
    private UUID bookId;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected UserDocument() {}

    public UserDocument(
            UUID ownerId, UUID bookId, String originalName, String contentType, long sizeBytes, String storageKey) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.bookId = bookId;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.status = DocumentStatus.STORED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getBookId() {
        return bookId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void processing() {
        status = DocumentStatus.PROCESSING;
        errorMessage = null;
    }

    public void ready(int count) {
        status = DocumentStatus.READY;
        chunkCount = count;
        errorMessage = null;
    }

    public void failed(String message) {
        status = DocumentStatus.FAILED;
        errorMessage =
                message == null ? "Unknown ingestion error" : message.substring(0, Math.min(message.length(), 2000));
    }
}
