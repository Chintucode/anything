package com.chintu.anything.plan;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** PUT /api/plans/{id}/skips: { "date": "2026-09-28", "skipped": true }. */
public record SkipRequest(
        @NotNull(message = "date is required.") LocalDate date,
        @NotNull(message = "skipped is required.") Boolean skipped) {
}
