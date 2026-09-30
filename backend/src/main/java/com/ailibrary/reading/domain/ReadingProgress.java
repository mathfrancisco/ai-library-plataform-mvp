package com.ailibrary.reading.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reading_progress", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "book_id"}))
public class ReadingProgress {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "book_id", nullable = false)
    private UUID bookId;

    @Column(name = "current_page", nullable = false)
    private int currentPage;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage = BigDecimal.ZERO;

    @Column(name = "started_at")
    private LocalDate startedAt;

    @Column(name = "completed_at")
    private LocalDate completedAt;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ReadingProgress() {}

    public ReadingProgress(UUID userId, UUID bookId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.bookId = bookId;
    }

    public void update(
            Integer currentPage, BigDecimal percentage, LocalDate startedAt, LocalDate completedAt, String notes) {
        if (currentPage != null) this.currentPage = currentPage;
        if (percentage != null) this.percentage = percentage;
        if (startedAt != null) this.startedAt = startedAt;
        if (completedAt != null) this.completedAt = completedAt;
        if (notes != null) this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getBookId() {
        return bookId;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public LocalDate getStartedAt() {
        return startedAt;
    }

    public LocalDate getCompletedAt() {
        return completedAt;
    }

    public String getNotes() {
        return notes;
    }
}
