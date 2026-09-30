package com.ailibrary.reading.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class ReadingDtos {
    private ReadingDtos() {}

    public record UpdateRequest(
            @Min(0) Integer currentPage,
            @DecimalMin("0") @DecimalMax("100") BigDecimal percentage,
            LocalDate startedAt,
            LocalDate completedAt,
            @Size(max = 5000) String notes) {}

    /** {@code exists=false} is an empty view for a book the user has not tracked yet (no 404). */
    public record ProgressView(
            UUID bookId,
            boolean exists,
            int currentPage,
            BigDecimal percentage,
            LocalDate startedAt,
            LocalDate completedAt,
            String notes) {

        public static ProgressView empty(UUID bookId) {
            return new ProgressView(bookId, false, 0, BigDecimal.ZERO, null, null, null);
        }
    }
}
