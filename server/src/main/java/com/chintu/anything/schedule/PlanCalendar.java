package com.chintu.anything.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Pure date math: where does a given calendar date fall in a plan?
 *
 * <p>Plans are counted in 7-day weeks from the start date. The weekday that decides
 * which workout you do ("### Mon") is the weekday of the <em>plan date</em>, which is
 * the calendar date minus any shift. So after one "Shift plan" (dayOffset = 1),
 * Monday's workout moves to Tuesday, and everything after it moves too.
 *
 * <p>No Spring, no database: easy to test and reason about.
 */
public final class PlanCalendar {

    public enum Status { NOT_STARTED, ACTIVE, FINISHED }

    /**
     * @param week           1-based plan week (only meaningful when ACTIVE)
     * @param weekday        plan weekday, used to find the "### Mon" section
     * @param planDate       the calendar date minus the shift
     * @param daysUntilStart days until the (shifted) start; 0 unless NOT_STARTED
     */
    public record Position(Status status, int week, DayOfWeek weekday, LocalDate planDate, long daysUntilStart) {
    }

    private PlanCalendar() {
    }

    public static Position locate(LocalDate startDate, int dayOffset, int weeks, LocalDate date) {
        LocalDate planDate = date.minusDays(dayOffset);
        long daysSinceStart = ChronoUnit.DAYS.between(startDate, planDate);

        if (daysSinceStart < 0) {
            return new Position(Status.NOT_STARTED, 0, planDate.getDayOfWeek(), planDate, -daysSinceStart);
        }

        long week = daysSinceStart / 7 + 1;
        if (week > weeks) {
            return new Position(Status.FINISHED, 0, planDate.getDayOfWeek(), planDate, 0);
        }
        return new Position(Status.ACTIVE, (int) week, planDate.getDayOfWeek(), planDate, 0);
    }
}
