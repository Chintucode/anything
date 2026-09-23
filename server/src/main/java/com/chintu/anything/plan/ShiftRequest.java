package com.chintu.anything.plan;

import jakarta.validation.constraints.NotNull;

/**
 * POST /api/plans/{id}/shift: { "days": 1 } moves the plan a day later,
 * { "days": -5 } starts it five days earlier.
 */
public record ShiftRequest(@NotNull(message = "days is required.") Integer days) {
}
