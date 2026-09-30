package com.ailibrary.document;

import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.common.error.BadRequestException;
import com.ailibrary.common.error.NotFoundException;
import com.ailibrary.document.domain.UserDocument;
import com.ailibrary.document.repository.UserDocumentRepository;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.apache.tika.Tika;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@EnableConfigurationProperties(UploadProperties.class)
public class DocumentService {
    private static final Tika TIKA = new Tika();
    private static final Set<String> EXT = Set.of("pdf", "epub", "txt", "md", "markdown");
    private final UserDocumentRepository docs;
    private final BookRepository books;
    private final UploadProperties props;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;

    public DocumentService(
            UserDocumentRepository docs,
            BookRepository books,
            UploadProperties props,
            ApplicationEventPublisher events,
            JdbcTemplate jdbc) {
        this.docs = docs;
        this.books = books;
        this.props = props;
        this.events = events;
        this.jdbc = jdbc;
    }

    @Transactional
    public DocumentView upload(UUID owner, UUID bookId, MultipartFile file) {
        if (file.isEmpty()) throw new BadRequestException("EMPTY_FILE", "File is empty");
        if (file.getSize() > props.maxBytes())
            throw new BadRequestException("FILE_TOO_LARGE", "File exceeds maximum size");
        if (bookId != null && !books.existsById(bookId)) throw NotFoundException.book();
        String original = displayName(file.getOriginalFilename());
        String ext = extension(original);
        if (!EXT.contains(ext))
            throw new BadRequestException("UNSUPPORTED_FILE_TYPE", "Allowed formats: PDF, EPUB, TXT, MD");
        String contentType = Optional.ofNullable(file.getContentType())
                .orElse("application/octet-stream")
                .toLowerCase(Locale.ROOT);
        if (!contentTypeAllowed(ext, contentType))
            throw new BadRequestException(
                    "UNSUPPORTED_FILE_TYPE", "File content type does not match an allowed document format");
        if (!detectedTypeAllowed(ext, detect(file)))
            throw new BadRequestException("UNSUPPORTED_FILE_TYPE", "File content does not match its extension");
        try {
            Path root = Path.of(props.dir()).toAbsolutePath().normalize();
            Files.createDirectories(root);
            String key = owner + "/" + UUID.randomUUID() + "." + ext;
            Path target = root.resolve(key).normalize();
            if (!target.startsWith(root)) throw new BadRequestException("Invalid path");
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            UserDocument doc = docs.save(new UserDocument(owner, bookId, original, contentType, file.getSize(), key));
            events.publishEvent(new DocumentUploadedEvent(doc.getId()));
            return view(doc);
        } catch (IOException e) {
            throw new BadRequestException("Could not store file");
        }
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

    @Transactional
    public void delete(UUID owner, UUID id) {
        UserDocument d = owned(owner, id);
        try {
            Files.deleteIfExists(Path.of(props.dir())
                    .toAbsolutePath()
                    .normalize()
                    .resolve(d.getStorageKey())
                    .normalize());
        } catch (IOException ignored) {
        }
        jdbc.update(
                "DELETE FROM vector_store WHERE metadata->>'documentId' = ? AND metadata->>'ownerId' = ?",
                id.toString(),
                owner.toString());
        docs.delete(d);
    }

    public Path path(UserDocument d) {
        return Path.of(props.dir())
                .toAbsolutePath()
                .normalize()
                .resolve(d.getStorageKey())
                .normalize();
    }

    public DocumentView view(UserDocument d) {
        return new DocumentView(
                d.getId(),
                d.getBookId(),
                d.getOriginalName(),
                d.getContentType(),
                d.getSizeBytes(),
                d.getStatus(),
                d.getErrorMessage(),
                d.getChunkCount(),
                d.getCreatedAt());
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
    /** Keeps only the last path segment of the client-supplied name; it is display metadata, never a path. */
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
