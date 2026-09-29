package com.ailibrary.library.repository;

import com.ailibrary.library.domain.LibraryStatus;
import com.ailibrary.library.domain.UserLibraryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserLibraryRepository extends JpaRepository<UserLibraryItem, UUID> {
    Optional<UserLibraryItem> findByUserIdAndBookId(UUID userId, UUID bookId);
    List<UserLibraryItem> findByUserIdOrderByAddedAtDesc(UUID userId);
    List<UserLibraryItem> findByUserIdAndStatusOrderByAddedAtDesc(UUID userId, LibraryStatus status);
    long countByUserId(UUID userId);
    long countByUserIdAndStatus(UUID userId, LibraryStatus status);

    @Query("select i.bookId from UserLibraryItem i where i.userId = :userId")
    List<UUID> findBookIds(@Param("userId") UUID userId);
}
