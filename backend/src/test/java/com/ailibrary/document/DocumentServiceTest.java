package com.ailibrary.document;

import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.common.error.BadRequestException;
import com.ailibrary.common.error.NotFoundException;
import com.ailibrary.document.repository.UserDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentServiceTest {
    @TempDir Path dir;
    private final UserDocumentRepository docs = mock(UserDocumentRepository.class);
    private final BookRepository books = mock(BookRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private DocumentService service() {
        return new DocumentService(docs, books, new UploadProperties(dir.toString(), 1024), events, jdbc);
    }

    @Test
    void storesAllowedFileUnderGeneratedKeyAndPublishesIngestion() throws Exception {
        when(docs.save(any())).thenAnswer(i -> i.getArgument(0));
        UUID owner = UUID.randomUUID();
        var view = service().upload(owner, null, new MockMultipartFile("file", "../../etc/notes.md", "text/markdown", "# hi".getBytes()));
        assertThat(view.originalName()).isEqualTo("notes.md");
        try (var files = Files.walk(dir)) {
            assertThat(files.filter(Files::isRegularFile).toList()).singleElement()
                    .satisfies(p -> assertThat(p.startsWith(dir.resolve(owner.toString()))).isTrue());
        }
        verify(events).publishEvent(any(DocumentUploadedEvent.class));
    }

    @Test
    void rejectsDisallowedExtensionMimeMismatchEmptyAndOversizedFiles() {
        UUID owner = UUID.randomUUID();
        assertThatThrownBy(() -> service().upload(owner, null, new MockMultipartFile("file", "run.exe", "application/octet-stream", new byte[]{1})))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Allowed formats");
        assertThatThrownBy(() -> service().upload(owner, null, new MockMultipartFile("file", "a.pdf", "text/html", new byte[]{1})))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("content type");
        assertThatThrownBy(() -> service().upload(owner, null, new MockMultipartFile("file", "a.txt", "text/plain", new byte[0])))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("empty");
        assertThatThrownBy(() -> service().upload(owner, null, new MockMultipartFile("file", "a.txt", "text/plain", new byte[2048])))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("maximum size");
        verifyNoInteractions(events);
    }

    @Test
    void anotherUsersDocumentIsNotFound() {
        UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
        when(docs.findByIdAndOwnerId(id, owner)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().delete(owner, id)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void displayNameStripsPathsAndControlCharacters() {
        assertThat(DocumentService.displayName("C:\\Users\\me\\book.pdf")).isEqualTo("book.pdf");
        assertThat(DocumentService.displayName("a\u0000b.txt")).isEqualTo("ab.txt");
        assertThat(DocumentService.displayName(null)).isEqualTo("upload");
    }
}
