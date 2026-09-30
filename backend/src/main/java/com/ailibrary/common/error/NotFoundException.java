package com.ailibrary.common.error;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {
    public NotFoundException(String message) {
        this("NOT_FOUND", message);
    }

    public NotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }

    public static NotFoundException book() {
        return new NotFoundException("BOOK_NOT_FOUND", "Book not found");
    }

    public static NotFoundException document() {
        return new NotFoundException("DOCUMENT_NOT_FOUND", "Document not found");
    }
}
