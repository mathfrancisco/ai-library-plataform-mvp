package com.ailibrary.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.common.error.AiException;
import com.ailibrary.library.service.LibraryService;
import com.ailibrary.reading.service.ReadingProgressService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelRoutingTest {
    private final AiFacade ai = mock(AiFacade.class);
    private final UUID user = UUID.randomUUID();

    @Test
    void assistantUsesSmartModel() {
        new AssistantService(
                        ai, mock(LibraryService.class), mock(ReadingProgressService.class), mock(BookRepository.class))
                .ask(user, "hi", List.of(new AssistantService.Turn(AssistantService.Role.USER, "earlier")));
        verify(ai)
                .tools(eq(user), eq("LIBRARY_ASSISTANT"), eq(ModelTier.SMART), anyString(), anyList(), eq("hi"), any());
    }

    @Test
    void historyKeepsTheMostRecentTurnsWithinBudget() {
        var turns = new java.util.ArrayList<AssistantService.Turn>();
        for (int i = 0; i < 15; i++) turns.add(new AssistantService.Turn(AssistantService.Role.USER, "turn " + i));
        var messages = AssistantService.toMessages(turns);
        assertThat(messages).hasSize(AssistantService.MAX_TURNS);
        assertThat(messages.getLast().getText()).isEqualTo("turn 14");
        var huge = List.of(new AssistantService.Turn(AssistantService.Role.USER, "x".repeat(5000)));
        assertThat(AssistantService.toMessages(huge)).isEmpty();
    }

    @Test
    void providerErrorsMapToStableCodes() {
        assertThat(((AiException) AiFacade.translate(new RuntimeException("HTTP 429 Too Many Requests"))).code())
                .isEqualTo("AI_RATE_LIMITED");
        assertThat(((AiException) AiFacade.translate(new RuntimeException(new java.net.SocketTimeoutException())))
                        .code())
                .isEqualTo("AI_TIMEOUT");
        assertThat(((AiException) AiFacade.translate(new IllegalStateException("boom"))).code())
                .isEqualTo("AI_PROVIDER_ERROR");
    }
}
