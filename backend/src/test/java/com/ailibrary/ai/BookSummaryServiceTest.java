package com.ailibrary.ai;

import com.ailibrary.ai.domain.AiGeneration;
import com.ailibrary.ai.repository.AiGenerationRepository;
import com.ailibrary.book.domain.Book;
import com.ailibrary.book.service.BookService;
import com.ailibrary.common.error.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static com.ailibrary.ai.BookSummaryService.SummaryType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookSummaryServiceTest {
    private final BookService books = mock(BookService.class);
    private final AiFacade ai = mock(AiFacade.class);
    private final AiGenerationRepository cache = mock(AiGenerationRepository.class);
    private final BookSummaryService service = new BookSummaryService(books, ai, cache);
    private final UUID user = UUID.randomUUID();

    @Test
    void cacheKeyChangesWithModelSourceAndTypeButNotWhitespace() {
        UUID id = UUID.randomUUID();
        String base = BookSummaryService.cacheKey(id, "Dune\nHerbert\nSpice", "m1", SHORT);
        assertThat(BookSummaryService.cacheKey(id, "  Dune \n Herbert\n\nSpice ", "m1", SHORT)).isEqualTo(base);
        assertThat(BookSummaryService.cacheKey(id, "Dune\nHerbert\nSpice", "m2", SHORT)).isNotEqualTo(base);
        assertThat(BookSummaryService.cacheKey(id, "Dune\nHerbert\nSpice!", "m1", SHORT)).isNotEqualTo(base);
        assertThat(BookSummaryService.cacheKey(id, "Dune\nHerbert\nSpice", "m1", TLDR)).isNotEqualTo(base);
    }

    @Test
    void generatesOnceThenServesFromCache() {
        Book book = new Book(null, null, "Dune", null, "Frank Herbert", null, "Desert planet.", null, null, null, null, null, false);
        when(books.getEntity(book.getId())).thenReturn(book);
        when(ai.properties()).thenReturn(new AiProperties(true, "openai", "m1", 30));
        when(ai.complete(any(), eq("BOOK_SUMMARY"), anyString(), anyString())).thenReturn("Summary");
        when(cache.findByPromptHash(anyString())).thenReturn(Optional.empty());

        assertThat(service.summarize(user, book.getId(), SHORT)).isEqualTo("Summary");
        verify(cache).save(any(AiGeneration.class));

        when(cache.findByPromptHash(anyString())).thenReturn(Optional.of(new AiGeneration(user, "BOOK", book.getId(), "SUMMARY_SHORT", "h", "openai", "m1", "Cached")));
        assertThat(service.summarize(user, book.getId(), SHORT)).isEqualTo("Cached");
        verify(ai, times(1)).complete(any(), any(), any(), any());
    }

    @Test
    void refusesToSummarizeWithoutSource() {
        Book book = new Book(null, null, "Empty", null, null, null, "  ", null, null, null, null, null, false);
        when(books.getEntity(book.getId())).thenReturn(book);
        assertThatThrownBy(() -> service.summarize(user, book.getId(), FULL)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(ai);
    }
}
