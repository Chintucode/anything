package com.chintu.anything.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.chintu.anything.schedule.PlanCalendar.Position;
import com.chintu.anything.schedule.PlanCalendar.Status;

class PlanCalendarTest {

    private static final LocalDate MONDAY_START = LocalDate.of(2026, 9, 28);
    private static final int WEEKS = 12;

    private static Position at(LocalDate date) {
        return PlanCalendar.locate(MONDAY_START, 0, WEEKS, date);
    }

    @Test
    void dayBeforeTheStartIsNotStarted() {
        Position p = at(LocalDate.of(2026, 9, 25));

        assertThat(p.status()).isEqualTo(Status.NOT_STARTED);
        assertThat(p.daysUntilStart()).isEqualTo(3);
    }

    @Test
    void startDateIsWeekOne() {
        Position p = at(MONDAY_START);

        assertThat(p.status()).isEqualTo(Status.ACTIVE);
        assertThat(p.week()).isEqualTo(1);
        assertThat(p.weekday()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void seventhDayIsStillWeekOneAndEighthIsWeekTwo() {
        assertThat(at(MONDAY_START.plusDays(6)).week()).isEqualTo(1);   // Sunday
        assertThat(at(MONDAY_START.plusDays(7)).week()).isEqualTo(2);   // next Monday
    }

    @Test
    void phaseBoundaryWeeks() {
        assertThat(at(MONDAY_START.plusDays(27)).week()).isEqualTo(4);  // last day of week 4
        assertThat(at(MONDAY_START.plusDays(28)).week()).isEqualTo(5);  // first day of week 5
    }

    @Test
    void lastDayIsWeekTwelveAndTheNextDayIsFinished() {
        LocalDate lastDay = LocalDate.of(2026, 12, 20);

        assertThat(at(lastDay).status()).isEqualTo(Status.ACTIVE);
        assertThat(at(lastDay).week()).isEqualTo(12);
        assertThat(at(lastDay.plusDays(1)).status()).isEqualTo(Status.FINISHED);
    }

    @Test
    void midweekStartKeepsCalendarWeekdays() {
        LocalDate wednesday = LocalDate.of(2026, 9, 30);
        Position nextMonday = PlanCalendar.locate(wednesday, 0, WEEKS, LocalDate.of(2026, 10, 5));

        assertThat(nextMonday.week()).isEqualTo(1);                     // day 6 of week 1
        assertThat(nextMonday.weekday()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void shiftMovesEveryWorkoutOneDayLater() {
        // Shifted by one day: on Tuesday you do Monday's workout.
        Position tuesday = PlanCalendar.locate(MONDAY_START, 1, WEEKS, MONDAY_START.plusDays(1));

        assertThat(tuesday.weekday()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(tuesday.week()).isEqualTo(1);
        assertThat(tuesday.planDate()).isEqualTo(MONDAY_START);
    }

    @Test
    void shiftAlsoPushesTheEndBack() {
        LocalDate originalLastDay = LocalDate.of(2026, 12, 20);

        assertThat(PlanCalendar.locate(MONDAY_START, 1, WEEKS, originalLastDay.plusDays(1)).status())
                .isEqualTo(Status.ACTIVE);
        assertThat(PlanCalendar.locate(MONDAY_START, 1, WEEKS, originalLastDay.plusDays(2)).status())
                .isEqualTo(Status.FINISHED);
    }

    @Test
    void shiftCanMakeTheStartDateItselfNotStarted() {
        Position p = PlanCalendar.locate(MONDAY_START, 2, WEEKS, MONDAY_START);

        assertThat(p.status()).isEqualTo(Status.NOT_STARTED);
        assertThat(p.daysUntilStart()).isEqualTo(2);
    }
}
