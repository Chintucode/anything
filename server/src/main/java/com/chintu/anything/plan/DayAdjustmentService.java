package com.chintu.anything.plan;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.chintu.anything.plan.PlanResponses.PlanDetail;
import com.chintu.anything.schedule.PlanCalendar;

/**
 * The two answers to a missed day.
 *
 * <p><b>Skip</b> writes the day off: it stops counting in progress and doesn't
 * break the streak. The rest of the plan stays on its original dates.
 *
 * <p><b>Shift</b> moves the whole plan later (or earlier, with a negative number),
 * so today becomes the day you missed. Plans that punish one missed day get deleted.
 *
 * <p>Also here: marking a rest day as taken, which is the opposite gesture — the
 * plan asked for nothing and you gave it exactly that.
 */
@Service
public class DayAdjustmentService {

    private final PlanRepository plans;
    private final SkippedDayRepository skips;
    private final RestedDayRepository rests;

    public DayAdjustmentService(PlanRepository plans, SkippedDayRepository skips, RestedDayRepository rests) {
        this.plans = plans;
        this.skips = skips;
        this.rests = rests;
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

    /**
     * "I took my rest day." Only valid on a day the plan left empty on purpose:
     * ticking a rest day you were never given would be a lie in the data.
     * Idempotent, like skipping.
     */
    @Transactional
    public RestResponse setRested(long planId, LocalDate date, boolean rested) {
        Plan plan = find(planId);

        if (PlanSchedule.locate(plan, date).status() != PlanCalendar.Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    date + " isn't part of this plan.");
        }
        if (PlanSchedule.dayOn(plan, date).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    date + " is a training day, not a rest day.");
        }

        var existing = rests.findByPlanIdAndRestedOn(planId, date);
        if (rested && existing.isEmpty()) {
            rests.save(new RestedDay(plan, date));
        } else if (!rested) {
            existing.ifPresent(rests::delete);
        }
        return new RestResponse(planId, date, rested);
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

    public record RestResponse(Long planId, LocalDate date, boolean rested) {
    }
}
