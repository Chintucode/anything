package com.chintu.anything.plan;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.chintu.anything.plan.TodayResponse.MissedDay;
import com.chintu.anything.plan.TodayResponse.NextWorkout;
import com.chintu.anything.plan.TodayResponse.Status;
import com.chintu.anything.plan.TodayResponse.TodayItem;
import com.chintu.anything.schedule.PlanCalendar;
import com.chintu.anything.schedule.PlanCalendar.Position;

/**
 * Works out "what do I do today?" for a plan.
 * Nothing is generated ahead of time: each day is calculated from the plan template.
 */
@Service
public class TodayService {

    /** How far ahead to look for the next workout (covers long gaps before a start date). */
    private static final int MAX_LOOKAHEAD_DAYS = 400;

    /** How far back to look for a day you didn't finish. */
    private static final int MAX_LOOKBACK_DAYS = 21;

    private final PlanRepository plans;
    private final CompletionRepository completions;
    private final SkippedDayRepository skips;

    public TodayService(PlanRepository plans, CompletionRepository completions, SkippedDayRepository skips) {
        this.plans = plans;
        this.completions = completions;
        this.skips = skips;
    }

    @Transactional(readOnly = true)
    public TodayResponse today(long planId, LocalDate date) {
        Plan plan = plans.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan " + planId + " not found."));

        Position pos = PlanSchedule.locate(plan, date);

        return switch (pos.status()) {
            case NOT_STARTED -> response(plan, date, pos, Status.NOT_STARTED, null, null, pos.daysUntilStart(),
                    nextWorkout(plan, date));
            case FINISHED -> response(plan, date, pos, Status.FINISHED, null, null, null, null);
            case ACTIVE -> {
                PlanPhase phase = PlanSchedule.phaseFor(plan, pos.week());
                Optional<PlanDay> day = PlanSchedule.dayAt(plan, pos);
                if (day.isEmpty()) {
                    yield response(plan, date, pos, Status.REST, phase, null, null, nextWorkout(plan, date));
                }
                Status status = skips.existsByPlanIdAndSkipOn(plan.getId(), date) ? Status.SKIPPED : Status.TRAINING;
                yield response(plan, date, pos, status, phase, day.get(), null, null);
            }
        };
    }

    /**
     * The most recent training day before {@code date}, if it was left unfinished and
     * not skipped. Only the latest one is reported: nobody needs a list of regrets.
     */
    private MissedDay missedDay(Plan plan, LocalDate date) {
        for (int i = 1; i <= MAX_LOOKBACK_DAYS; i++) {
            LocalDate candidate = date.minusDays(i);
            if (candidate.isBefore(PlanSchedule.firstDay(plan))) {
                return null;
            }
            Optional<PlanDay> day = PlanSchedule.dayOn(plan, candidate);
            if (day.isEmpty()) {
                continue;
            }
            if (skips.existsByPlanIdAndSkipOn(plan.getId(), candidate)) {
                return null;   // already dealt with
            }
            List<PlanItem> dayItems = day.get().getItems();
            int total = dayItems.size();
            int done = (int) completions.findByPlanIdAndDoneOnBetween(plan.getId(), candidate, candidate).stream()
                    .filter(c -> dayItems.stream().anyMatch(item -> item.getId().equals(c.getItem().getId())))
                    .count();
            return done < total
                    ? new MissedDay(candidate, candidate.getDayOfWeek(), day.get().getTitle(), done, total)
                    : null;    // the last workout was finished: nothing to answer for
        }
        return null;
    }

    /** First training day after {@code date}, or null if the plan ends before one. */
    private static NextWorkout nextWorkout(Plan plan, LocalDate date) {
        for (int i = 1; i <= MAX_LOOKAHEAD_DAYS; i++) {
            LocalDate candidate = date.plusDays(i);
            Position pos = PlanSchedule.locate(plan, candidate);
            if (pos.status() == PlanCalendar.Status.FINISHED) {
                return null;
            }
            if (pos.status() == PlanCalendar.Status.ACTIVE) {
                Optional<PlanDay> day = PlanSchedule.dayAt(plan, pos);
                if (day.isPresent()) {
                    return new NextWorkout(candidate, candidate.getDayOfWeek(), day.get().getTitle());
                }
            }
        }
        return null;
    }

    private TodayResponse response(Plan plan, LocalDate date, Position pos, Status status,
            PlanPhase phase, PlanDay day, Long daysUntilStart, NextWorkout next) {

        List<TodayItem> items = null;
        Integer doneCount = null;
        if (day != null) {
            Map<Long, Completion> doneByItem = completions.findByPlanIdAndDoneOnBetween(plan.getId(), date, date)
                    .stream()
                    .collect(Collectors.toMap(c -> c.getItem().getId(), c -> c, (a, b) -> a));
            items = day.getItems().stream()
                    .map(i -> {
                        Completion done = doneByItem.get(i.getId());
                        return new TodayItem(i.getId(), i.getName(), i.getSets(), i.reps(), i.getRestSeconds(),
                                i.getNote(), done != null, done != null ? done.getActualReps() : null);
                    })
                    .toList();
            doneCount = (int) items.stream().filter(TodayItem::done).count();
        }

        return new TodayResponse(
                plan.getId(),
                plan.getTitle(),
                status,
                date,
                date.getDayOfWeek(),
                pos.status() == PlanCalendar.Status.ACTIVE ? pos.week() : null,
                plan.getWeeks(),
                phase != null ? phase.getName() : null,
                day != null ? day.getTitle() : null,
                items,
                doneCount,
                daysUntilStart,
                next,
                missedDay(plan, date));
    }
}
