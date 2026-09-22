package com.chintu.anything.plan;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** PUT /api/plans/{id}/completions: { "itemId": 12, "date": "2026-09-28", "done": true }. */
public record CompletionRequest(
        @NotNull(message = "itemId is required.") Long itemId,
        @NotNull(message = "date is required.") LocalDate date,
        @NotNull(message = "done is required.") Boolean done) {
}
