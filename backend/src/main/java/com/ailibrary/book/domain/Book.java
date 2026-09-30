package com.ailibrary.book.domain;

import com.ailibrary.book.service.BookFingerprint;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "books")
public class Book {
    @Id
    private UUID id;

    @Column(length = 13)
    private String isbn13;

    @Column(length = 10)
    private String isbn10;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 500)
    private String subtitle;

    @Column(name = "author_names")
    private String authorNames;

    @Column(name = "category_names")
    private String categoryNames;

    @Column(columnDefinition = "text")
    private String description;

    private String language;
    private String publisher;

    @Column(name = "published_year")
    private Integer publishedYear;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "cover_url", columnDefinition = "text")
    private String coverUrl;

    /** Normalized title used for deduplication; see BookFingerprint.normalizeText. */
    @Column(name = "title_key", length = 500)
    private String titleKey;

    @Column(name = "public_domain", nullable = false)
    private boolean publicDomain;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Book() {}

    public Book(
            String isbn13,
            String isbn10,
            String title,
            String subtitle,
            String authorNames,
            String categoryNames,
            String description,
            String language,
            String publisher,
            Integer publishedYear,
            Integer pageCount,
            String coverUrl,
            boolean publicDomain) {
        this.id = UUID.randomUUID();
        this.isbn13 = isbn13;
        this.isbn10 = isbn10;
        this.title = title;
        this.titleKey = BookFingerprint.normalizeText(title);
        this.subtitle = subtitle;
        this.authorNames = authorNames;
        this.categoryNames = categoryNames;
        this.description = description;
        this.language = language;
        this.publisher = publisher;
        this.publishedYear = publishedYear;
        this.pageCount = pageCount;
        this.coverUrl = coverUrl;
        this.publicDomain = publicDomain;
    }

    public UUID getId() {
        return id;
    }

    public String getIsbn13() {
        return isbn13;
    }

    public String getIsbn10() {
        return isbn10;
    }

    public void refreshTitleKey() {
        this.titleKey = BookFingerprint.normalizeText(title);
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getAuthorNames() {
        return authorNames;
    }

    public String getCategoryNames() {
        return categoryNames;
    }

    public String getDescription() {
        return description;
    }

    public String getLanguage() {
        return language;
    }

    public String getPublisher() {
        return publisher;
    }

    public Integer getPublishedYear() {
        return publishedYear;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public boolean isPublicDomain() {
        return publicDomain;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
