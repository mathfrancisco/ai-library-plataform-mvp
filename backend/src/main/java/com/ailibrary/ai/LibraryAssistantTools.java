package com.ailibrary.ai;

import com.ailibrary.library.domain.LibraryStatus;
import com.ailibrary.library.dto.LibraryDtos.LibraryItemView;
import com.ailibrary.library.dto.LibraryDtos.UpsertRequest;
import com.ailibrary.library.service.LibraryService;
import com.ailibrary.reading.dto.ReadingDtos.ProgressView;
import com.ailibrary.reading.service.ReadingProgressService;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;
import java.util.UUID;

public class LibraryAssistantTools {
    private final UUID userId; private final LibraryService library; private final ReadingProgressService reading;
    public LibraryAssistantTools(UUID userId, LibraryService library, ReadingProgressService reading){ this.userId=userId; this.library=library; this.reading=reading; }

    @Tool(description="List books from the authenticated user's library, optionally by status WANT_TO_READ, READING, READ, or DROPPED")
    public List<LibraryItemView> getMyBooksByStatus(String status){ return library.list(userId, status==null||status.isBlank()?null:LibraryStatus.valueOf(status)); }

    @Tool(description="List books the authenticated user is currently reading")
    public List<LibraryItemView> getCurrentlyReading(){ return library.list(userId, LibraryStatus.READING); }

    @Tool(description="Get reading progress for a local book id owned by the authenticated user's reading data")
    public ProgressView getReadingProgress(String bookId){ return reading.get(userId, UUID.fromString(bookId)); }

    @Tool(description="Add an existing local book to the authenticated user's library with a reading status")
    public LibraryItemView addLocalBookToLibrary(String bookId, String status){
        return library.upsert(userId, UUID.fromString(bookId), new UpsertRequest(LibraryStatus.valueOf(status), null, null));
    }
}
