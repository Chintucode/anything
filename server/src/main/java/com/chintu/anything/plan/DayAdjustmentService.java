package com.chintu.anything.plan;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.chintu.anything.plan.PlanResponses.PlanDetail;

/**
 * The two answers to a missed day.
 *
 * <p><b>Skip</b> writes the day off: it stops counting in progress and doesn't
 * break the streak. The rest of the plan stays on its original dates.
 *
 * <p><b>Shift</b> moves the whole plan later (or earlier, with a negative number),
 * so today becomes the day you missed. Plans that punish one missed day get deleted.
 */
@Service
public class DayAdjustmentService {

    private final PlanRepository plans;
    private final SkippedDayRepository skips;

    public DayAdjustmentService(PlanRepository plans, SkippedDayRepository skips) {
        this.plans = plans;
        this.skips = skips;
    }

    /** Idempotent: skipping twice leaves one row, unskipping something never skipped is fine. */
    @Transactional
    public SkipResponse setSkipped(long planId, LocalDate date, boolean skipped) {
        Plan plan = find(planId);

        if (PlanSchedule.dayOn(plan, date).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "There's no workout scheduled on " + date + ".");
        }

        var existing = skips.findByPlanIdAndSkipOn(planId, date);
        if (skipped && existing.isEmpty()) {
            skips.save(new SkippedDay(plan, date));
        } else if (!skipped) {
            existing.ifPresent(skips::delete);
        }
        return new SkipResponse(planId, date, skipped);
    }

    /** Moves every remaining day of the plan by {@code days} (negative moves it earlier). */
    @Transactional
    public PlanDetail shift(long planId, int days) {
        Plan plan = find(planId);
        if (days == 0) {
            return PlanDetail.from(plan);
        }
        if (Math.abs(days) > 365) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That's more than a year. Use a smaller shift.");
        }
        plan.shiftBy(days);
        return PlanDetail.from(plan);
    }

    private Plan find(long planId) {
        return plans.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan " + planId + " not found."));
    }

    public record SkipResponse(Long planId, LocalDate date, boolean skipped) {
    }
}
