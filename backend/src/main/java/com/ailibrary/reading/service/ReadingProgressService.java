package com.ailibrary.reading.service;

import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.common.error.NotFoundException;
import com.ailibrary.reading.domain.ReadingProgress;
import com.ailibrary.reading.dto.ReadingDtos.ProgressView;
import com.ailibrary.reading.dto.ReadingDtos.UpdateRequest;
import com.ailibrary.reading.repository.ReadingProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ReadingProgressService {
    private final ReadingProgressRepository progress;
    private final BookRepository books;

    public ReadingProgressService(ReadingProgressRepository progress, BookRepository books) {
        this.progress = progress;
        this.books = books;
    }

    @Transactional(readOnly = true)
    public ProgressView get(UUID userId, UUID bookId) {
        ReadingProgress p = progress.findByUserIdAndBookId(userId, bookId)
                .orElseThrow(() -> new NotFoundException("Reading progress not found"));
        return view(p);
    }

    @Transactional
    public ProgressView upsert(UUID userId, UUID bookId, UpdateRequest request) {
        if (!books.existsById(bookId)) throw new NotFoundException("Book not found");
        ReadingProgress p = progress.findByUserIdAndBookId(userId, bookId)
                .orElseGet(() -> new ReadingProgress(userId, bookId));
        p.update(request.currentPage(), request.percentage(), request.startedAt(), request.completedAt(), request.notes());
        return view(progress.save(p));
    }

    private ProgressView view(ReadingProgress p) {
        return new ProgressView(p.getBookId(), p.getCurrentPage(), p.getPercentage(), p.getStartedAt(), p.getCompletedAt(), p.getNotes());
    }
}
