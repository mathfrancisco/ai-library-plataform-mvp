package com.ailibrary.document;

import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.common.error.BadRequestException;
import com.ailibrary.common.error.ErrorCode;
import com.ailibrary.common.error.NotFoundException;
import com.ailibrary.document.domain.DocumentStatus;
import com.ailibrary.document.domain.UserDocument;
import com.ailibrary.document.repository.UserDocumentRepository;
import com.ailibrary.library.domain.LibraryStatus;
import com.ailibrary.library.dto.LibraryDtos.UpsertRequest;
import com.ailibrary.library.service.LibraryService;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@EnableConfigurationProperties(UploadProperties.class)
public class DocumentService {
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final Tika TIKA = new Tika();
    private static final Set<String> EXT = Set.of("pdf", "epub", "txt", "md", "markdown");

    private final UserDocumentRepository docs;
    private final BookRepository books;
    private final UploadProperties props;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;
    private final LibraryService library;

    public DocumentService(
            UserDocumentRepository docs,
            BookRepository books,
            UploadProperties props,
            ApplicationEventPublisher events,
            JdbcTemplate jdbc,
            LibraryService library) {
        this.docs = docs;
        this.books = books;
        this.props = props;
        this.events = events;
        this.jdbc = jdbc;
        this.library = library;
    }

    @Transactional
    public DocumentView upload(UUID owner, UUID bookId, MultipartFile file) {
        if (file.isEmpty()) throw new BadRequestException(ErrorCode.EMPTY_FILE, "File is empty");
        if (file.getSize() > props.maxBytes())
            throw new BadRequestException(ErrorCode.FILE_TOO_LARGE, "File exceeds maximum size");
        if (bookId != null && !books.existsById(bookId)) throw NotFoundException.book();
        checkQuota(owner, file.getSize());
        String original = displayName(file.getOriginalFilename());
        String ext = extension(original);
        if (!EXT.contains(ext))
            throw new BadRequestException(ErrorCode.UNSUPPORTED_FILE_TYPE, "Allowed formats: PDF, EPUB, TXT, MD");
        String contentType = Optional.ofNullable(file.getContentType())
                .orElse("application/octet-stream")
                .toLowerCase(Locale.ROOT);
        if (!contentTypeAllowed(ext, contentType))
            throw new BadRequestException(
                    ErrorCode.UNSUPPORTED_FILE_TYPE, "File content type does not match an allowed document format");
        if (!detectedTypeAllowed(ext, detect(file)))
            throw new BadRequestException(ErrorCode.UNSUPPORTED_FILE_TYPE, "File content does not match its extension");

        Path root = root();
        String key = owner + "/" + UUID.randomUUID() + "." + ext;
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw new BadRequestException("Invalid path");
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            log.warn("Could not store upload for {}: {}", owner, e.toString());
            throw new BadRequestException("Could not store file");
        }
        // If the transaction rolls back, the file must not stay behind (SPEC-04 §12.9e).
        onRollback(() -> deleteQuietly(target));
        UserDocument doc = docs.save(new UserDocument(owner, bookId, original, contentType, file.getSize(), key));
        // A document attached to a book puts that book on the shelf as READING (SPEC-04 §8.9).
        if (bookId != null) library.add(owner, bookId, new UpsertRequest(LibraryStatus.READING, null, null));
        events.publishEvent(new DocumentUploadedEvent(doc.getId()));
        return view(doc);
    }

    /** Re-runs ingestion, e.g. after a failure or an embedding model change (SPEC-04 §8.8). */
    @Transactional
    public DocumentView reingest(UUID owner, UUID id) {
        UserDocument doc = owned(owner, id);
        if (doc.getStatus() == DocumentStatus.PROCESSING)
            throw new BadRequestException(ErrorCode.DOCUMENT_NOT_READY, "Document is being processed");
        doc.queued();
        docs.save(doc);
        events.publishEvent(new DocumentUploadedEvent(doc.getId()));
        return view(doc);
    }

    @Transactional(readOnly = true)
    public List<DocumentView> list(UUID owner) {
        return docs.findByOwnerIdOrderByCreatedAtDesc(owner).stream()
                .map(this::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserDocument owned(UUID owner, UUID id) {
        return docs.findByIdAndOwnerId(id, owner).orElseThrow(NotFoundException::document);
    }

    /** Vector rows and the DB row go in the transaction; the file is removed only after commit (SPEC-04 §8.5). */
    @Transactional
    public void delete(UUID owner, UUID id) {
        UserDocument d = owned(owner, id);
        Path file = path(d);
        deleteVectors(owner, id);
        docs.delete(d);
        onCommit(() -> deleteQuietly(file));
    }

    public void deleteVectors(UUID owner, UUID documentId) {
        jdbc.update(
                "DELETE FROM vector_store WHERE metadata->>'documentId' = ? AND metadata->>'ownerId' = ?",
                documentId.toString(),
                owner.toString());
    }

    public Path path(UserDocument d) {
        return root().resolve(d.getStorageKey()).normalize();
    }

    public DocumentView view(UserDocument d) {
        IngestionFailure failure =
                d.getStatus() == DocumentStatus.FAILED ? IngestionFailure.fromStored(d.getErrorMessage()) : null;
        return new DocumentView(
                d.getId(),
                d.getBookId(),
                d.getOriginalName(),
                d.getContentType(),
                d.getSizeBytes(),
                d.getStatus(),
                failure == null ? null : failure.name(),
                failure == null ? null : failure.message(),
                d.getChunkCount(),
                d.getCreatedAt());
    }

    private void checkQuota(UUID owner, long incoming) {
        long count = docs.countByOwnerId(owner);
        if (count >= props.maxDocumentsPerUser())
            throw new BadRequestException(
                    ErrorCode.QUOTA_EXCEEDED, "Document limit reached (" + props.maxDocumentsPerUser() + ")");
        long used = docs.sumSizeByOwnerId(owner);
        if (used + incoming > props.maxBytesPerUser())
            throw new BadRequestException(ErrorCode.QUOTA_EXCEEDED, "Storage limit reached for your account");
    }

    private Path root() {
        return Path.of(props.dir()).toAbsolutePath().normalize();
    }

    private static void onCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static void onRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) action.run();
            }
        });
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            log.warn("Could not delete {}: {}", file, ex.toString());
        }
    }

    static boolean contentTypeAllowed(String ext, String type) {
        if ("application/octet-stream".equals(type)) return true;
        return switch (ext) {
            case "pdf" -> type.equals("application/pdf");
            case "epub" -> type.equals("application/epub+zip") || type.equals("application/zip");
            case "txt" -> type.startsWith("text/plain");
            case "md", "markdown" -> type.startsWith("text/plain") || type.equals("text/markdown");
            default -> false;
        };
    }

    /** Content sniffing: the declared Content-Type and extension are client-controlled, the magic bytes are not. */
    private static String detect(MultipartFile file) {
        try (var in = file.getInputStream()) {
            return TIKA.detect(in).toLowerCase(Locale.ROOT);
        } catch (IOException e) {
            throw new BadRequestException("Could not read uploaded file");
        }
    }

    static boolean detectedTypeAllowed(String ext, String detected) {
        return switch (ext) {
            case "pdf" -> detected.equals("application/pdf");
            case "epub" -> detected.equals("application/epub+zip") || detected.equals("application/zip");
            case "txt", "md", "markdown" -> detected.startsWith("text/");
            default -> false;
        };
    }

    /** Keeps only the last path segment of the client-supplied name; it is display metadata, never a path. */
    static String displayName(String raw) {
        String n = raw == null ? "" : raw.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (n.isEmpty()) n = "upload";
        return n.length() > 255 ? n.substring(n.length() - 255) : n;
    }

    static String extension(String n) {
        int i = n.lastIndexOf('.');
        return i < 0 ? "" : n.substring(i + 1).toLowerCase(Locale.ROOT);
    }
}
