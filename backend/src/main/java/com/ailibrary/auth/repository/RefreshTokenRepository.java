package com.ailibrary.auth.repository;

import com.ailibrary.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAll(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query(
            "delete from RefreshToken t where t.expiresAt < :cutoff or (t.revokedAt is not null and t.revokedAt < :cutoff)")
    int deleteStale(@Param("cutoff") Instant cutoff);
}
