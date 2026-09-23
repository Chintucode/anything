package com.chintu.anything.plan;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/plans/{id}/progress.
 *
 * @param percent    completed ÷ dueSoFar, 0-100
 * @param completed  exercises ticked so far
 * @param dueSoFar   exercises the plan asked for up to and including today
 * @param streak     scheduled sessions in a row fully completed (rest days don't break it)
 * @param totalItems exercises in the whole plan
 * @param weeks      one bar per plan week
 */
public record ProgressResponse(
        Long planId,
        LocalDate date,
        int percent,
        int completed,
        int dueSoFar,
        int streak,
        int totalItems,
        List<WeekBar> weeks) {

    public record WeekBar(int week, int scheduled, int completed) {
    }
}
