package com.chintu.anything.plan;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Seven days, Monday to Sunday, for the calendar week containing a date.
 * Feeds the strip at the top of Today, so you can see the week at a glance
 * and go back to fill in a day you forgot to tick.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WeekResponse(Long planId, LocalDate from, LocalDate to, List<WeekDay> days) {

    /**
     * @param planWeek which week of the plan this day belongs to, null outside the plan
     * @param title    the day's name when it's a training day
     */
    public record WeekDay(
            LocalDate date,
            DayOfWeek weekday,
            TodayResponse.Status status,
            Integer planWeek,
            String title,
            int done,
            int total) {
    }
}
