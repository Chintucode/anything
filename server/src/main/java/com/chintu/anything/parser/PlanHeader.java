package com.chintu.anything.parser;

/**
 * The header block at the top of a plan (between the two --- lines).
 *
 * <p>{@code length} is measured in whatever unit the plan is scheduled by: weeks for a
 * weekly program, days for a sequential course. The two schedules are genuinely
 * different animals — see {@link Schedule}.
 *
 * @param category a free label (workout, meditation, study). The app doesn't behave
 *                 differently for it; it's there so a person can tell their plans apart.
 */
public record PlanHeader(int formatVersion, String title, String category, Schedule schedule, int length) {

    public enum Schedule {
        /**
         * "### Mon: Push" — days are weekdays, repeating every week inside a phase.
         * The plan has opinions about <em>when</em>: miss Monday and Monday is gone.
         */
        WEEKLY,

        /**
         * "### Day 9: Naming Thoughts" — days run one after another. The plan has
         * opinions about <em>order</em>, not dates: Day 9 is the ninth thing you do,
         * whenever you do it, so nothing can be missed.
         */
        SEQUENTIAL
    }

    public boolean isWeekly() {
        return schedule == Schedule.WEEKLY;
    }

    /** Weeks the plan spans — its own number when weekly, rounded up from days when not. */
    public int weeks() {
        return schedule == Schedule.WEEKLY ? length : (length + 6) / 7;
    }

    /** The unit phase headings are numbered in: "week" or "day". */
    public String unit() {
        return schedule == Schedule.WEEKLY ? "week" : "day";
    }
}
