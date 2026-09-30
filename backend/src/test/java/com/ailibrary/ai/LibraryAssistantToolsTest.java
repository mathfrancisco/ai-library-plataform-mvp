package com.ailibrary.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ailibrary.book.dto.BookView;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.library.domain.LibraryStatus;
import com.ailibrary.library.dto.LibraryDtos.LibraryItemView;
import com.ailibrary.library.dto.LibraryDtos.UpsertRequest;
import com.ailibrary.library.service.LibraryService;
import com.ailibrary.reading.dto.ReadingDtos.ProgressView;
import com.ailibrary.reading.service.ReadingProgressService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LibraryAssistantToolsTest {
    private final LibraryService library = mock(LibraryService.class);
    private final UUID user = UUID.randomUUID();
    private final LibraryAssistantTools tools =
            new LibraryAssistantTools(user, library, mock(ReadingProgressService.class), mock(BookRepository.class));

    @Test
    void toolsAreBoundToTheAuthenticatedUserAndReturnCompactRecords() {
        UUID book = UUID.randomUUID();
        BookView view = new BookView(
                book,
                null,
                null,
                "Dune",
                null,
                List.of("Frank Herbert"),
                List.of(),
                "long text",
                null,
                null,
                null,
                null,
                null,
                false);
        when(library.addOrUpdate(eq(user), eq(book), any()))
                .thenReturn(new LibraryItemView(
                        view, LibraryStatus.READING, false, null, null, null, ProgressView.empty(book)));
        var result = tools.addLocalBookToLibrary(book.toString(), "reading");
        verify(library).addOrUpdate(user, book, new UpsertRequest(LibraryStatus.READING, null, null));
        assertThat(result.error()).isNull();
        assertThat(result.data().title()).isEqualTo("Dune");
    }

    @Test
    void badModelInputBecomesAnErrorStringInsteadOfAnException() {
        assertThat(tools.getReadingProgress("Dune").error()).contains("findLocalBooks");
        assertThat(tools.addLocalBookToLibrary(UUID.randomUUID().toString(), "delete everything")
                        .error())
                .contains("Unknown status");
        assertThatThrownBy(() -> LibraryAssistantTools.parseStatus("nope"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(LibraryAssistantTools.parseStatus("want to read")).isEqualTo(LibraryStatus.WANT_TO_READ);
    }

    @Test
    void toolCallsAreCappedPerQuestion() {
        for (int i = 0; i < LibraryAssistantTools.MAX_CALLS; i++) tools.getCurrentlyReading();
        assertThat(tools.getCurrentlyReading().error()).contains("limit");
    }
}
