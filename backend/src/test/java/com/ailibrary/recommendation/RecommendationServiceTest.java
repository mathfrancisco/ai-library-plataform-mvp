package com.ailibrary.recommendation;

import com.ailibrary.book.domain.Book;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.common.vector.VectorStoreAccess;
import com.ailibrary.library.domain.LibraryStatus;
import com.ailibrary.library.domain.UserLibraryItem;
import com.ailibrary.library.repository.UserLibraryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecommendationServiceTest {
    private final UserLibraryRepository library = mock(UserLibraryRepository.class);
    private final BookRepository books = mock(BookRepository.class);
    private final VectorStoreAccess vectors = mock(VectorStoreAccess.class);
    private final RecommendationService service = new RecommendationService(library, books, vectors);
    private final UUID user = UUID.randomUUID();

    private static Book book(String title, String authors, String categories, String isbn) {
        return new Book(isbn, null, title, null, authors, categories, null, null, null, null, null, null, false);
    }

    @Test
    void recommendsFromFavoriteAuthorsAndExcludesShelfAndDuplicateEditions() {
        Book dune = book("Dune", "Frank Herbert", "Science fiction", "9780441013593");
        Book dropped = book("Boring Book", "Someone", "Romance", null);
        Book messiah = book("Dune Messiah", "Frank Herbert", "Science fiction", null);
        Book foundation = book("Foundation", "Isaac Asimov", "Science fiction", null);
        Book duneOtherRow = book("Dune (reprint)", "Frank Herbert", null, "9780441013593");
        UserLibraryItem fav = new UserLibraryItem(user, dune.getId(), LibraryStatus.READ);
        fav.update(null, true, 5);
        UserLibraryItem drop = new UserLibraryItem(user, dropped.getId(), LibraryStatus.DROPPED);
        when(library.findByUserIdOrderByAddedAtDesc(user)).thenReturn(List.of(fav, drop));
        when(books.findAllById(anyCollection())).thenReturn(List.of(dune, dropped));
        when(books.lexicalSearch(anyString(), anyInt())).thenReturn(List.of(foundation, dune, duneOtherRow, messiah));
        when(vectors.store()).thenReturn(Optional.empty());

        var result = service.forUser(user, 10);

        assertThat(result).extracting(r -> r.book().title()).containsExactly("Dune Messiah", "Foundation");
        assertThat(result.getFirst().reasons()).contains("More from Frank Herbert");
        assertThat(result.get(1).reasons()).containsExactly("Matches your interest in Science fiction");
        verify(books).lexicalSearch(argThat(q -> q.contains("\"Frank Herbert\"") && !q.contains("Romance")), anyInt());
    }

    @Test
    void coldStartReturnsRecentBooks() {
        when(library.findByUserIdOrderByAddedAtDesc(user)).thenReturn(List.of());
        when(books.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(book("New", "A", null, null))));
        assertThat(service.forUser(user, 3)).singleElement().satisfies(r -> assertThat(r.reasons()).containsExactly("Recently added to the catalog"));
    }

    @Test
    void droppedAndLowRatedBooksAreNotSeeds() {
        UserLibraryItem low = new UserLibraryItem(user, UUID.randomUUID(), LibraryStatus.READ);
        low.update(null, null, 1);
        assertThat(RecommendationService.seedWeight(low)).isZero();
        assertThat(RecommendationService.seedWeight(new UserLibraryItem(user, UUID.randomUUID(), LibraryStatus.DROPPED))).isZero();
        assertThat(RecommendationService.seedWeight(new UserLibraryItem(user, UUID.randomUUID(), LibraryStatus.READING))).isEqualTo(1.0);
    }
}
