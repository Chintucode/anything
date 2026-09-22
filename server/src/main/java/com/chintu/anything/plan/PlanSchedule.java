package com.chintu.anything.plan;

import java.time.LocalDate;
import java.util.Optional;

import com.chintu.anything.schedule.PlanCalendar;
import com.chintu.anything.schedule.PlanCalendar.Position;

/** Looks up which phase and day of a saved plan fall on a calendar date. */
final class PlanSchedule {

    private PlanSchedule() {
    }

    static Position locate(Plan plan, LocalDate date) {
        return PlanCalendar.locate(plan.getStartDate(), plan.getDayOffset(), plan.getWeeks(), date);
    }

    /** A saved plan always covers every week (the parser checks), so a phase always exists. */
    static PlanPhase phaseFor(Plan plan, int week) {
        return plan.getPhases().stream()
                .filter(p -> p.getFromWeek() <= week && week <= p.getToWeek())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No phase covers week " + week));
    }

    /** The training day at this position, or empty for a rest day. Position must be ACTIVE. */
    static Optional<PlanDay> dayAt(Plan plan, Position pos) {
        return phaseFor(plan, pos.week()).getDays().stream()
                .filter(d -> d.getWeekday() == pos.weekday())
                .findFirst();
    }

    /** The training day on this calendar date, or empty (rest day, not started, finished). */
    static Optional<PlanDay> dayOn(Plan plan, LocalDate date) {
        Position pos = locate(plan, date);
        return pos.status() == PlanCalendar.Status.ACTIVE ? dayAt(plan, pos) : Optional.empty();
    }

    /** First day of the plan on the calendar, after any shift. */
    static LocalDate firstDay(Plan plan) {
        return plan.getStartDate().plusDays(plan.getDayOffset());
    }
}
