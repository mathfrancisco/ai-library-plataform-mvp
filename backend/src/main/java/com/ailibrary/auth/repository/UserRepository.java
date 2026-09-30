package com.ailibrary.auth.repository;

import com.ailibrary.auth.domain.User;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Modifying
    @Query(
            """
            update User u set u.role = com.ailibrary.auth.domain.Role.ADMIN
            where lower(u.email) in :emails and u.role <> com.ailibrary.auth.domain.Role.ADMIN
            """)
    int promoteToAdmin(@Param("emails") Collection<String> emails);
}
