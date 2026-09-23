package com.chintu.anything.plan;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * PUT /api/plans/{id}/completions:
 * { "itemId": 12, "date": "2026-09-28", "done": true, "actualReps": 8 }.
 *
 * <p>{@code actualReps} is optional: leave it out (or null) when you did it as written.
 */
public record CompletionRequest(
        @NotNull(message = "itemId is required.") Long itemId,
        @NotNull(message = "date is required.") LocalDate date,
        @NotNull(message = "done is required.") Boolean done,
        @Min(value = 0, message = "actualReps can't be negative.")
        @Max(value = 1000, message = "actualReps is too large.")
        Integer actualReps) {
}
