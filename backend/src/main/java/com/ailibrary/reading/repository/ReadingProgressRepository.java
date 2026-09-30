package com.ailibrary.reading.repository;

import com.ailibrary.reading.domain.ReadingProgress;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface ReadingProgressRepository extends JpaRepository<ReadingProgress, UUID> {
    Optional<ReadingProgress> findByUserIdAndBookId(UUID userId, UUID bookId);

    List<ReadingProgress> findByUserId(UUID userId);

    List<ReadingProgress> findByUserIdAndBookIdIn(UUID userId, Collection<UUID> bookIds);

    @Modifying
    void deleteByUserIdAndBookId(UUID userId, UUID bookId);
}
