package com.ailibrary.common.error;

public class NotFoundException extends ApiException {
    public NotFoundException(String message) {
        this(ErrorCode.NOT_FOUND, message);
    }

    public NotFoundException(ErrorCode code, String message) {
        super(code, message);
    }

    public static NotFoundException book() {
        return new NotFoundException(ErrorCode.BOOK_NOT_FOUND, "Book not found");
    }

    public static NotFoundException document() {
        return new NotFoundException(ErrorCode.DOCUMENT_NOT_FOUND, "Document not found");
    }
}
