package com.ailibrary.reading.repository;

import com.ailibrary.reading.domain.ReadingProgress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReadingProgressRepository extends JpaRepository<ReadingProgress, UUID> {
    Optional<ReadingProgress> findByUserIdAndBookId(UUID userId, UUID bookId);

    List<ReadingProgress> findByUserId(UUID userId);
}
