package com.chintu.anything.plan;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import com.chintu.anything.plan.PlanResponses.ItemView;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What the Today screen shows for one plan on one date.
 *
 * <ul>
 *   <li>TRAINING: {@code week}, {@code phaseName}, {@code dayTitle} and {@code items} are set</li>
 *   <li>REST: a scheduled rest day; {@code next} points at the next workout</li>
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
        List<ItemView> items,
        Long daysUntilStart,
        NextWorkout next) {

    public enum Status { TRAINING, REST, NOT_STARTED, FINISHED }

    /** The next scheduled workout, shown on rest days and before the plan starts. */
    public record NextWorkout(LocalDate date, DayOfWeek weekday, String title) {
    }
}
