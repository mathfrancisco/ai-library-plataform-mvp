package com.ailibrary.book.repository;

import com.ailibrary.book.domain.Book;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, UUID> {
    Optional<Book> findByIsbn13(String isbn13);

    List<Book> findByIsbn10(String isbn10);

    List<Book> findTop20ByTitleKey(String titleKey);

    List<Book> findTop500ByTitleKeyIsNull();

    @Query(
            value =
                    """
            SELECT b.*
            FROM books b
            WHERE b.search_document @@ websearch_to_tsquery('simple', :query)
            ORDER BY ts_rank_cd(b.search_document, websearch_to_tsquery('simple', :query)) DESC
            LIMIT :limit
            """,
            nativeQuery = true)
    List<Book> lexicalSearch(@Param("query") String query, @Param("limit") int limit);
}
