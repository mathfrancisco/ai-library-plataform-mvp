package com.ailibrary.auth.service;

import com.ailibrary.auth.repository.UserRepository;
import com.ailibrary.document.DocumentService;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes an account and everything it owns. Library items, reading progress, document rows and refresh tokens
 * cascade from users by FK; uploaded files, vector rows and AI logs/generations are removed explicitly.
 */
@Service
public class AccountDeletionService {
    private final UserRepository users;
    private final DocumentService documents;
    private final JdbcTemplate jdbc;

    public AccountDeletionService(UserRepository users, DocumentService documents, JdbcTemplate jdbc) {
        this.users = users;
        this.documents = documents;
        this.jdbc = jdbc;
    }

    @Transactional
    public void delete(UUID userId) {
        documents.list(userId).forEach(d -> documents.delete(userId, d.id()));
        jdbc.update("DELETE FROM vector_store WHERE metadata->>'ownerId' = ?", userId.toString());
        jdbc.update("DELETE FROM ai_request_logs WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM ai_generations WHERE user_id = ?", userId);
        users.deleteById(userId);
    }
}
