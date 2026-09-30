package com.ailibrary.book.service;

import java.util.UUID;

/** Published when a book row is created; vector indexing runs after the transaction commits. */
public record BookSavedEvent(UUID bookId) {}
