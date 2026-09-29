package com.ailibrary.reading.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

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
            String notes
    ) {}

    public record ProgressView(UUID bookId, int currentPage, BigDecimal percentage, LocalDate startedAt,
                               LocalDate completedAt, String notes) {}
}
