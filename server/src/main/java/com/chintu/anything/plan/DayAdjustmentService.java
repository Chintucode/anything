package com.chintu.anything.plan;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

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
 * so today becomes the day you missed — carrying everything you've already recorded
 * with it. Plans that punish one missed day get deleted.
 *
 * <p>Also here: marking a rest day as taken, which is the opposite gesture — the
 * plan asked for nothing and you gave it exactly that.
 */
@Service
public class DayAdjustmentService {

    private final PlanRepository plans;
    private final SkippedDayRepository skips;
    private final RestedDayRepository rests;
    private final CompletionRepository completions;

    public DayAdjustmentService(PlanRepository plans, SkippedDayRepository skips, RestedDayRepository rests,
            CompletionRepository completions) {
        this.plans = plans;
        this.skips = skips;
        this.rests = rests;
        this.completions = completions;
    }

    /** Idempotent: skipping twice leaves one row, unskipping something never skipped is fine. */
    @Transactional
    public SkipResponse setSkipped(long planId, LocalDate date, boolean skipped) {
        Plan plan = find(planId);
        if (plan.isSequential()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Day-by-day plans don't skip days. An unfinished day simply waits for you.");
        }

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
        if (plan.isSequential()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Day-by-day plans have no rest days to mark: the next day is whenever you're ready.");
        }

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

    /**
     * Moves the whole plan by {@code days} (negative moves it earlier), and moves
     * everything you have already recorded along with it.
     *
     * <p>Every tick, skip and rest is stored against a calendar date, and which workout
     * a date means is that date minus the plan's shift. So moving the plan without
     * moving the records leaves them pointing at the wrong day — which read, on screen,
     * as every one of them vanishing: the ring, the streak and all twelve week bars
     * dropped to zero the moment you tapped the button offered for a missed day.
     * Shifting is "treat my plan as if it started later", so the work moves too.
     *
     * <p>The rows are moved in the order that keeps each destination free: latest first
     * when moving forward, earliest first when moving back. Both tables are unique on
     * their date, and moving a row onto one that hasn't moved out of the way yet is a
     * constraint violation. Each write is flushed before the next for the same reason.
     */
    @Transactional
    public PlanDetail shift(long planId, int days) {
        Plan plan = find(planId);
        if (plan.isSequential()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Day-by-day plans aren't tied to the calendar, so there's nothing to shift.");
        }
        if (days == 0) {
            return PlanDetail.from(plan);
        }
        if (Math.abs(days) > 365) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That's more than a year. Use a smaller shift.");
        }

        moveAll(completions.findByPlanId(planId), Completion::getDoneOn, days, completions::flush,
                c -> c.moveBy(days));
        moveAll(skips.findByPlanId(planId), SkippedDay::getSkipOn, days, skips::flush,
                s -> s.moveBy(days));
        moveAll(rests.findByPlanId(planId), RestedDay::getRestedOn, days, rests::flush,
                r -> r.moveBy(days));

        plan.shiftBy(days);
        return PlanDetail.from(plan);
    }

    /** Moves dated rows one at a time, latest-first or earliest-first so none collide. */
    private static <T> void moveAll(List<T> rows, Function<T, LocalDate> dateOf, int days,
            Runnable flush, Consumer<T> move) {

        Comparator<T> order = Comparator.comparing(dateOf);
        rows.sort(days > 0 ? order.reversed() : order);
        for (T row : rows) {
            move.accept(row);
            flush.run();
        }
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
