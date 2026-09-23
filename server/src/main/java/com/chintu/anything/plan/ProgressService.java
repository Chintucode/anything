package com.chintu.anything.plan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.chintu.anything.plan.ProgressResponse.WeekBar;
import com.chintu.anything.schedule.PlanCalendar;
import com.chintu.anything.schedule.PlanCalendar.Position;

/**
 * Progress numbers for a plan on a given date.
 *
 * <p>One fraction, one meaning: {@code completed} ÷ {@code dueSoFar}, where dueSoFar is
 * everything the plan asked for up to and including today. It dips in the morning and
 * climbs back as the day is worked through.
 *
 * <p>An earlier version excluded today's unticked exercises from both sides, so that a
 * morning couldn't lower the number. The result was a number that couldn't move at all
 * for anyone keeping up — always 100, whatever you did. Today's own progress now has its
 * own ring on the day's card, which is where that gentleness belongs.
 *
 * <p>The streak stays forgiving: an unfinished today doesn't break it, because the day
 * isn't over yet. A past training day that wasn't fully done does break it, unless it
 * was skipped — a skipped day stops counting altogether.
 */
@Service
public class ProgressService {

    private final PlanRepository plans;
    private final CompletionRepository completions;
    private final SkippedDayRepository skips;

    public ProgressService(PlanRepository plans, CompletionRepository completions, SkippedDayRepository skips) {
        this.plans = plans;
        this.completions = completions;
        this.skips = skips;
    }

    @Transactional(readOnly = true)
    public ProgressResponse progress(long planId, LocalDate date) {
        Plan plan = plans.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan " + planId + " not found."));

        LocalDate first = PlanSchedule.firstDay(plan);
        LocalDate last = plan.endDate();

        // All ticks for the whole plan, grouped by date: date -> item ids.
        Map<LocalDate, Set<Long>> doneByDate = new HashMap<>();
        for (Completion c : completions.findByPlanIdAndDoneOnBetween(plan.getId(), first, last)) {
            doneByDate.computeIfAbsent(c.getDoneOn(), d -> new HashSet<>()).add(c.getItem().getId());
        }

        Set<LocalDate> skipped = skips.findByPlanId(plan.getId()).stream()
                .map(SkippedDay::getSkipOn)
                .collect(java.util.stream.Collectors.toSet());

        int[] weekScheduled = new int[plan.getWeeks() + 1];
        int[] weekCompleted = new int[plan.getWeeks() + 1];
        int completedSoFar = 0;
        int dueSoFar = 0;
        int totalItems = 0;

        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            Position pos = PlanSchedule.locate(plan, d);
            if (pos.status() != PlanCalendar.Status.ACTIVE) {
                continue;
            }
            Optional<PlanDay> day = PlanSchedule.dayAt(plan, pos);
            if (day.isEmpty() || skipped.contains(d)) {
                continue; // rest day, or a day the user wrote off
            }
            int due = day.get().getItems().size();
            int done = doneCount(day.get(), doneByDate.get(d));

            totalItems += due;
            weekScheduled[pos.week()] += due;
            weekCompleted[pos.week()] += done;

            if (!d.isAfter(date)) {
                completedSoFar += done;
                dueSoFar += due;
            }
        }

        List<WeekBar> weeks = new ArrayList<>();
        for (int w = 1; w <= plan.getWeeks(); w++) {
            weeks.add(new WeekBar(w, weekScheduled[w], weekCompleted[w]));
        }

        int percent = dueSoFar == 0 ? 0 : Math.round(100f * completedSoFar / dueSoFar);
        int streak = streak(plan, date, first, doneByDate, skipped);

        return new ProgressResponse(plan.getId(), date, percent, completedSoFar,
                dueSoFar, streak, totalItems, weeks);
    }

    /** Training days in a row, counting back from {@code date}, where every exercise was done. */
    private static int streak(Plan plan, LocalDate date, LocalDate first,
            Map<LocalDate, Set<Long>> doneByDate, Set<LocalDate> skipped) {
        LocalDate from = date.isAfter(plan.endDate()) ? plan.endDate() : date;
        int streak = 0;
        for (LocalDate d = from; !d.isBefore(first); d = d.minusDays(1)) {
            Optional<PlanDay> day = PlanSchedule.dayOn(plan, d);
            if (day.isEmpty() || skipped.contains(d)) {
                continue; // rest days and skipped days neither count nor break the streak
            }
            boolean complete = doneCount(day.get(), doneByDate.get(d)) == day.get().getItems().size();
            if (complete) {
                streak++;
            } else if (d.isEqual(date)) {
                continue; // today isn't over yet
            } else {
                break;
            }
        }
        return streak;
    }

    private static int doneCount(PlanDay day, Set<Long> doneIds) {
        if (doneIds == null) {
            return 0;
        }
        return (int) day.getItems().stream().filter(i -> doneIds.contains(i.getId())).count();
    }
}
