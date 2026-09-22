package com.chintu.anything.plan;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.chintu.anything.plan.PlanResponses.ItemView;
import com.chintu.anything.plan.TodayResponse.NextWorkout;
import com.chintu.anything.plan.TodayResponse.Status;
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

    private final PlanRepository plans;

    public TodayService(PlanRepository plans) {
        this.plans = plans;
    }

    @Transactional(readOnly = true)
    public TodayResponse today(long planId, LocalDate date) {
        Plan plan = plans.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan " + planId + " not found."));

        Position pos = locate(plan, date);

        return switch (pos.status()) {
            case NOT_STARTED -> response(plan, date, pos, Status.NOT_STARTED, null, null, pos.daysUntilStart(),
                    nextWorkout(plan, date));
            case FINISHED -> response(plan, date, pos, Status.FINISHED, null, null, null, null);
            case ACTIVE -> {
                PlanPhase phase = phaseFor(plan, pos.week());
                Optional<PlanDay> day = dayFor(phase, pos);
                yield day.isPresent()
                        ? response(plan, date, pos, Status.TRAINING, phase, day.get(), null, null)
                        : response(plan, date, pos, Status.REST, phase, null, null, nextWorkout(plan, date));
            }
        };
    }

    private static Position locate(Plan plan, LocalDate date) {
        return PlanCalendar.locate(plan.getStartDate(), plan.getDayOffset(), plan.getWeeks(), date);
    }

    /** The saved plan always covers every week (the parser checks), so a phase always exists. */
    private static PlanPhase phaseFor(Plan plan, int week) {
        return plan.getPhases().stream()
                .filter(p -> p.getFromWeek() <= week && week <= p.getToWeek())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No phase covers week " + week));
    }

    private static Optional<PlanDay> dayFor(PlanPhase phase, Position pos) {
        return phase.getDays().stream()
                .filter(d -> d.getWeekday() == pos.weekday())
                .findFirst();
    }

    /** First training day after {@code date}, or null if the plan ends before one. */
    private static NextWorkout nextWorkout(Plan plan, LocalDate date) {
        for (int i = 1; i <= MAX_LOOKAHEAD_DAYS; i++) {
            LocalDate candidate = date.plusDays(i);
            Position pos = locate(plan, candidate);
            if (pos.status() == PlanCalendar.Status.FINISHED) {
                return null;
            }
            if (pos.status() == PlanCalendar.Status.ACTIVE) {
                Optional<PlanDay> day = dayFor(phaseFor(plan, pos.week()), pos);
                if (day.isPresent()) {
                    return new NextWorkout(candidate, candidate.getDayOfWeek(), day.get().getTitle());
                }
            }
        }
        return null;
    }

    private static TodayResponse response(Plan plan, LocalDate date, Position pos, Status status,
            PlanPhase phase, PlanDay day, Long daysUntilStart, NextWorkout next) {
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
                day != null ? day.getItems().stream().map(ItemView::from).toList() : null,
                daysUntilStart,
                next);
    }
}
