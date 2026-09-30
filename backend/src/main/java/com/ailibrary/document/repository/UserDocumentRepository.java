package com.ailibrary.document.repository;

import com.ailibrary.document.domain.DocumentStatus;
import com.ailibrary.document.domain.UserDocument;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserDocumentRepository extends JpaRepository<UserDocument, UUID> {
    List<UserDocument> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    Optional<UserDocument> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<UserDocument> findByOwnerIdAndBookIdAndStatus(UUID ownerId, UUID bookId, DocumentStatus status);

    List<UserDocument> findByStatusIn(Collection<DocumentStatus> statuses);

    List<UserDocument> findByStatusAndCreatedAtBefore(DocumentStatus status, Instant before);

    long countByOwnerId(UUID ownerId);

    @Query("select coalesce(sum(d.sizeBytes), 0) from UserDocument d where d.ownerId = :ownerId")
    long sumSizeByOwnerId(@Param("ownerId") UUID ownerId);
}
