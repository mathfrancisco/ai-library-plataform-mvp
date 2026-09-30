package com.ailibrary.ai;

import com.ailibrary.library.domain.LibraryStatus;
import com.ailibrary.library.dto.LibraryDtos.UpsertRequest;
import com.ailibrary.library.service.LibraryService;
import com.ailibrary.reading.service.ReadingProgressService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LibraryAssistantToolsTest {
    @Test
    void toolsAreBoundToTheAuthenticatedUser() {
        LibraryService library = mock(LibraryService.class);
        UUID user = UUID.randomUUID(), book = UUID.randomUUID();
        var tools = new LibraryAssistantTools(user, library, mock(ReadingProgressService.class));
        tools.addLocalBookToLibrary(book.toString(), "currently reading".replace("currently ", ""));
        verify(library).upsert(eq(user), eq(book), eq(new UpsertRequest(LibraryStatus.READING, null, null)));
    }

    @Test
    void parsesStatusLeniently() {
        assertThat(LibraryAssistantTools.parseStatus("want to read")).isEqualTo(LibraryStatus.WANT_TO_READ);
        assertThat(LibraryAssistantTools.parseStatus("read")).isEqualTo(LibraryStatus.READ);
        assertThatThrownBy(() -> LibraryAssistantTools.parseStatus("delete everything")).hasMessageContaining("Unknown status");
    }

    @Test
    void rejectsNonUuidBookIds() {
        var tools = new LibraryAssistantTools(UUID.randomUUID(), mock(LibraryService.class), mock(ReadingProgressService.class));
        assertThatThrownBy(() -> tools.getReadingProgress("Dune")).hasMessageContaining("UUID");
    }
}
