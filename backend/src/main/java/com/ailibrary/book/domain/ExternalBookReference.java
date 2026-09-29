package com.ailibrary.book.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "external_book_refs", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "external_id"}))
public class ExternalBookReference {
    @Id
    private UUID id;

    @Column(name = "book_id", nullable = false)
    private UUID bookId;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "source_url", columnDefinition = "text")
    private String sourceUrl;

    protected ExternalBookReference() {}

    public ExternalBookReference(UUID bookId, String provider, String externalId, String sourceUrl) {
        this.id = UUID.randomUUID();
        this.bookId = bookId;
        this.provider = provider;
        this.externalId = externalId;
        this.sourceUrl = sourceUrl;
    }

    public UUID getId() { return id; }
    public UUID getBookId() { return bookId; }
    public String getProvider() { return provider; }
    public String getExternalId() { return externalId; }
    public String getSourceUrl() { return sourceUrl; }
}
