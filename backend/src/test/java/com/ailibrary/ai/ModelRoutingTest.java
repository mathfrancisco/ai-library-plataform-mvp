package com.ailibrary.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ailibrary.common.error.AiException;
import com.ailibrary.library.service.LibraryService;
import com.ailibrary.reading.service.ReadingProgressService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelRoutingTest {
    private final AiFacade ai = mock(AiFacade.class);
    private final UUID user = UUID.randomUUID();

    @Test
    void assistantUsesSmartModel() {
        new AssistantService(ai, mock(LibraryService.class), mock(ReadingProgressService.class)).ask(user, "hi");
        verify(ai).tools(eq(user), eq("LIBRARY_ASSISTANT"), eq(ModelTier.SMART), anyString(), eq("hi"), any());
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
