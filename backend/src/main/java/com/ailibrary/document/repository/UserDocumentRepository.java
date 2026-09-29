package com.ailibrary.document.repository;
import com.ailibrary.document.domain.DocumentStatus;
import com.ailibrary.document.domain.UserDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface UserDocumentRepository extends JpaRepository<UserDocument,UUID>{
    List<UserDocument> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    Optional<UserDocument> findByIdAndOwnerId(UUID id,UUID ownerId);
    List<UserDocument> findByOwnerIdAndBookIdAndStatus(UUID ownerId,UUID bookId,DocumentStatus status);
}
