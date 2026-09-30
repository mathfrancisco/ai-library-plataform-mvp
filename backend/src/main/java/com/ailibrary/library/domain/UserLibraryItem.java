package com.ailibrary.library.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_library", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "book_id"}))
public class UserLibraryItem {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "book_id", nullable = false)
    private UUID bookId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LibraryStatus status;

    @Column(nullable = false)
    private boolean favorite;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer rating;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt = Instant.now();

    protected UserLibraryItem() {}

    public UserLibraryItem(UUID userId, UUID bookId, LibraryStatus status) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.bookId = bookId;
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getBookId() { return bookId; }
    public LibraryStatus getStatus() { return status; }
    public boolean isFavorite() { return favorite; }
    public Integer getRating() { return rating; }
    public Instant getAddedAt() { return addedAt; }

    public void update(LibraryStatus status, Boolean favorite, Integer rating) {
        if (status != null) this.status = status;
        if (favorite != null) this.favorite = favorite;
        if (rating != null) this.rating = rating;
    }
}
