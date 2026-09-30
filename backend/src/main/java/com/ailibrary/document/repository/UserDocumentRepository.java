package com.ailibrary.document.repository;

import com.ailibrary.document.domain.DocumentStatus;
import com.ailibrary.document.domain.UserDocument;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDocumentRepository extends JpaRepository<UserDocument, UUID> {
    List<UserDocument> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    Optional<UserDocument> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<UserDocument> findByOwnerIdAndBookIdAndStatus(UUID ownerId, UUID bookId, DocumentStatus status);

    List<UserDocument> findByStatusIn(Collection<DocumentStatus> statuses);
}
