package com.ailibrary.book.repository;

import com.ailibrary.book.domain.ExternalBookReference;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalBookReferenceRepository extends JpaRepository<ExternalBookReference, UUID> {
    Optional<ExternalBookReference> findByProviderAndExternalId(String provider, String externalId);
}
