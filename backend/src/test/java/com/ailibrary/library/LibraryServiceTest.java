package com.ailibrary.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ailibrary.book.domain.Book;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.common.error.NotFoundException;
import com.ailibrary.library.domain.LibraryStatus;
import com.ailibrary.library.domain.UserLibraryItem;
import com.ailibrary.library.dto.LibraryDtos.UpsertRequest;
import com.ailibrary.library.repository.UserLibraryRepository;
import com.ailibrary.library.service.LibraryService;
import com.ailibrary.reading.service.ReadingProgressService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LibraryServiceTest {
    private final UserLibraryRepository repo = mock(UserLibraryRepository.class);
    private final BookRepository books = mock(BookRepository.class);
    private final ReadingProgressService reading = mock(ReadingProgressService.class);
    private final LibraryService service = new LibraryService(repo, books, reading);
    private final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
    private final Book book =
            new Book(null, null, "Dune", null, "Frank Herbert", null, null, null, null, null, null, null, false);

    @BeforeEach
    void setUp() {
        when(books.findById(book.getId())).thenReturn(Optional.of(book));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void newItemDefaultsToWantToRead() {
        when(repo.findByUserIdAndBookId(alice, book.getId())).thenReturn(Optional.empty());
        var view = service.upsert(alice, book.getId(), new UpsertRequest(null, true, 5));
        assertThat(view.status()).isEqualTo(LibraryStatus.WANT_TO_READ);
        assertThat(view.favorite()).isTrue();
        assertThat(view.rating()).isEqualTo(5);
        verify(reading).onStatusChanged(alice, book.getId(), LibraryStatus.WANT_TO_READ);
    }

    @Test
    void statusTransitionNotifiesReadingOnlyWhenStatusChanges() {
        UserLibraryItem item = new UserLibraryItem(alice, book.getId(), LibraryStatus.READING);
        when(repo.findByUserIdAndBookId(alice, book.getId())).thenReturn(Optional.of(item));
        service.upsert(alice, book.getId(), new UpsertRequest(null, true, null));
        verify(reading, never()).onStatusChanged(any(), any(), any());
        service.upsert(alice, book.getId(), new UpsertRequest(LibraryStatus.READ, null, null));
        verify(reading).onStatusChanged(alice, book.getId(), LibraryStatus.READ);
        assertThat(item.getStatus()).isEqualTo(LibraryStatus.READ);
    }

    @Test
    void usersCannotRemoveEachOthersItems() {
        when(repo.findByUserIdAndBookId(bob, book.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.remove(bob, book.getId())).isInstanceOf(NotFoundException.class);
        verify(repo, never()).delete(any());
    }

    @Test
    void unknownBookIsRejected() {
        UUID missing = UUID.randomUUID();
        when(books.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.upsert(alice, missing, new UpsertRequest(null, null, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Book not found");
    }
}
