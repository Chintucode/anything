package com.chintu.anything.plan;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** JSON body for POST /api/plans: { "text": "...", "startDate": "2026-09-28" }. */
public record CreatePlanRequest(
        @NotBlank(message = "Paste a plan first.")
        @Size(max = PlanController.MAX_PLAN_LENGTH, message = "The plan is too long (max 100,000 characters).")
        String text,

        @NotNull(message = "Pick a start date.")
        LocalDate startDate) {
}
