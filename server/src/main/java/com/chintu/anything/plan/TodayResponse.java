package com.chintu.anything.plan;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import com.chintu.anything.parser.Reps;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What the Today screen shows for one plan on one date.
 *
 * <ul>
 *   <li>TRAINING: {@code week}, {@code phaseName}, {@code dayTitle}, {@code items} and {@code doneCount} are set</li>
 *   <li>REST: a scheduled rest day; {@code next} points at the next workout and
 *       {@code rested} says whether you've ticked it off</li>
 *   <li>SKIPPED: a training day you wrote off; the exercises are still listed</li>
 *   <li>NOT_STARTED: {@code daysUntilStart} and {@code next} (the first workout) are set</li>
 *   <li>FINISHED: the plan is over</li>
 * </ul>
 * Fields that don't apply are left out of the JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TodayResponse(
        Long planId,
        String planTitle,
        Status status,
        LocalDate date,
        DayOfWeek weekday,
        Integer week,
        int totalWeeks,
        String phaseName,
        String dayTitle,
        List<TodayItem> items,
        Integer doneCount,
        Long daysUntilStart,
        NextWorkout next,
        MissedDay missed,
        /** Rest days only: true once you've marked the day as taken. */
        Boolean rested) {

    public enum Status { TRAINING, REST, SKIPPED, NOT_STARTED, FINISHED }

    /** The most recent training day that was left unfinished, if there is one. */
    public record MissedDay(LocalDate date, DayOfWeek weekday, String title, int done, int total) {
    }

    /**
     * One exercise on today's list.
     *
     * @param actualReps what you actually managed, when it differed from the plan; null otherwise
     */
    public record TodayItem(Long id, String name, int sets, Reps reps, Integer restSeconds, String note,
            boolean done, Integer actualReps) {
    }

    /** The next scheduled workout, shown on rest days and before the plan starts. */
    public record NextWorkout(LocalDate date, DayOfWeek weekday, String title) {
    }
}
