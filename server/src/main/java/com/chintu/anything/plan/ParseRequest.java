package com.chintu.anything.plan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** JSON body for POST /api/plans/parse: { "text": "---\nanything: 1\n..." }. */
public record ParseRequest(
        @NotBlank(message = "Paste a plan first.")
        @Size(max = PlanController.MAX_PLAN_LENGTH, message = "The plan is too long (max 100,000 characters).")
        String text) {
}
