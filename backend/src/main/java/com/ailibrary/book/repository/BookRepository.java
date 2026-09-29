package com.ailibrary.book.repository;

import com.ailibrary.book.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookRepository extends JpaRepository<Book, UUID> {
    Optional<Book> findByIsbn13(String isbn13);

    @Query(value = """
            SELECT b.*
            FROM books b
            WHERE b.search_document @@ websearch_to_tsquery('simple', :query)
            ORDER BY ts_rank_cd(b.search_document, websearch_to_tsquery('simple', :query)) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Book> lexicalSearch(@Param("query") String query, @Param("limit") int limit);

    @Query(value = """
            SELECT * FROM books
            WHERE lower(title) = lower(:title)
              AND (:author = '' OR lower(coalesce(author_names,'')) LIKE lower(concat('%', :author, '%')))
            LIMIT 1
            """, nativeQuery = true)
    Optional<Book> findFingerprint(@Param("title") String title, @Param("author") String author);
}
