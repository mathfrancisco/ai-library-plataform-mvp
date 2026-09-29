package com.ailibrary.reading.repository;

import com.ailibrary.reading.domain.ReadingProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReadingProgressRepository extends JpaRepository<ReadingProgress, UUID> {
    Optional<ReadingProgress> findByUserIdAndBookId(UUID userId, UUID bookId);
    List<ReadingProgress> findByUserId(UUID userId);
}
