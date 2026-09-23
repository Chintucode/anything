package com.chintu.anything.plan;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** PUT /api/plans/{id}/rests: { "date": "2026-09-24", "rested": true }. */
public record RestRequest(
        @NotNull(message = "date is required.") LocalDate date,
        @NotNull(message = "rested is required.") Boolean rested) {
}
