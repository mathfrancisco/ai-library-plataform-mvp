package com.ailibrary.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ailibrary.book.domain.Book;
import com.ailibrary.common.error.BadRequestException;
import com.ailibrary.reading.dto.ReadingDtos.UpdateRequest;
import com.ailibrary.reading.repository.ReadingProgressRepository;
import com.ailibrary.reading.service.ReadingProgressService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReadingProgressServiceTest {
    private final ReadingProgressRepository repo = mock(ReadingProgressRepository.class);
    private final ReadingProgressService service = new ReadingProgressService(repo);
    private final UUID user = UUID.randomUUID();
    private final Book book =
            new Book(null, null, "Dune", null, "Frank Herbert", null, null, null, null, null, 400, null, false);

    @BeforeEach
    void setUp() {
        when(repo.findByUserIdAndBookId(any(), any())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void missingProgressIsAnEmptyViewNot404() {
        var view = service.get(user, book.getId());
        assertThat(view.exists()).isFalse();
        assertThat(view.percentage()).isEqualByComparingTo("0");
    }

    @Test
    void derivesPercentageFromPage() {
        var view = service.save(user, book, new UpdateRequest(100, null, null, null, null));
        assertThat(view.exists()).isTrue();
        assertThat(view.percentage()).isEqualByComparingTo("25.00");
    }

    @Test
    void rejectsPageBeyondBookAndCompletionBeforeStart() {
        assertThatThrownBy(() -> service.save(user, book, new UpdateRequest(401, null, null, null, null)))
                .isInstanceOf(BadRequestException.class);
        LocalDate today = LocalDate.of(2026, 9, 30);
        assertThatThrownBy(
                        () -> service.save(user, book, new UpdateRequest(null, null, today, today.minusDays(1), null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void applyNeverOverwritesExistingDates() {
        LocalDate first = LocalDate.of(2026, 1, 1);
        service.apply(user, book.getId(), null, null, first, null);
        var view = service.apply(user, book.getId(), 400, BigDecimal.valueOf(100), LocalDate.of(2026, 9, 30), first);
        assertThat(view.currentPage()).isEqualTo(400);
        assertThat(view.completedAt()).isEqualTo(first);
    }
}
