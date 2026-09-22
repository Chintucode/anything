package com.chintu.anything.plan;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/plans/{id}/progress.
 *
 * @param percent    completed ÷ scheduled so far, 0-100 (see ProgressService for the "today" rule)
 * @param completed  exercises ticked so far
 * @param scheduled  exercises due so far
 * @param streak     training days in a row fully completed (rest days don't break it)
 * @param totalItems exercises in the whole plan
 * @param weeks      one bar per plan week
 */
public record ProgressResponse(
        Long planId,
        LocalDate date,
        int percent,
        int completed,
        int scheduled,
        int streak,
        int totalItems,
        List<WeekBar> weeks) {

    public record WeekBar(int week, int scheduled, int completed) {
    }
}
