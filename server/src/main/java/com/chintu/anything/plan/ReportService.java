package com.chintu.anything.plan;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.chintu.anything.plan.ProgressResponse.WeekBar;
import com.chintu.anything.schedule.PlanCalendar;
import com.chintu.anything.schedule.PlanCalendar.Position;

/**
 * The round trip: a plain-text summary of how the plan is actually going,
 * written to be pasted straight back into the AI that wrote the plan.
 *
 * <p>It ends with the ask, so the user doesn't have to phrase it: adjust the
 * remaining weeks and send the whole plan back in the Anything format.
 */
@Service
public class ReportService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);

    private final PlanRepository plans;
    private final CompletionRepository completions;
    private final SkippedDayRepository skips;
    private final ProgressService progressService;
    private final CourseService courses;

    public ReportService(PlanRepository plans, CompletionRepository completions,
            SkippedDayRepository skips, ProgressService progressService, CourseService courses) {
        this.plans = plans;
        this.completions = completions;
        this.skips = skips;
        this.progressService = progressService;
        this.courses = courses;
    }

    @Transactional(readOnly = true)
    public String report(long planId, LocalDate date) {
        Plan plan = plans.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan " + planId + " not found."));

        if (plan.isSequential()) {
            return courseReport(plan, date);
        }

        ProgressResponse progress = progressService.progress(planId, date);
        Position pos = PlanSchedule.locate(plan, date);
        StringBuilder out = new StringBuilder();

        out.append("Here's how my plan is actually going. Read it and adjust the rest of the plan.\n\n");
        out.append("PLAN\n");
        out.append("- Title: ").append(plan.getTitle()).append('\n');
        out.append("- Length: ").append(plan.getWeeks()).append(" weeks, starting ")
                .append(PlanSchedule.firstDay(plan).format(DAY)).append('\n');
        out.append("- Today: ").append(date.format(DAY));
        if (pos.status() == PlanCalendar.Status.ACTIVE) {
            out.append(" — week ").append(pos.week()).append(" of ").append(plan.getWeeks());
        } else if (pos.status() == PlanCalendar.Status.NOT_STARTED) {
            out.append(" — not started yet");
        } else {
            out.append(" — the plan has finished");
        }
        out.append("\n\n");

        out.append("PROGRESS\n");
        out.append("- ").append(progress.completed()).append(" of ").append(progress.dueSoFar())
                .append(" scheduled exercises done so far (").append(progress.percent()).append("%)\n");
        out.append("- Current streak: ").append(progress.streak())
                .append(progress.streak() == 1 ? " session\n" : " sessions in a row\n");
        out.append("- Week by week (done/scheduled): ").append(weekLine(progress.weeks())).append("\n\n");

        List<String> shortfalls = shortfalls(plan, date);
        out.append("WHAT DIDN'T GO TO PLAN\n");
        if (shortfalls.isEmpty()) {
            out.append("- Nothing so far: everything scheduled has been done as written.\n");
        } else {
            shortfalls.forEach(line -> out.append("- ").append(line).append('\n'));
        }

        out.append("\nWHAT I WANT\n");
        out.append("Adjust the remaining weeks based on this — keep what's working, ");
        out.append("scale back what I keep missing, and keep the same training days.\n");
        out.append("Send the WHOLE updated plan again in the Anything format ");
        out.append("(same rules: --- header with anything/title/category/weeks, ");
        out.append("## Weeks A-B: Name, ### Mon: Title, and \"- Exercise | sets x reps | rest 60s | note: ...\").\n");

        return out.toString();
    }

    /**
     * The same report for a day-by-day course. What an AI needs to adjust a course is
     * different: not which weekdays were missed, but where you've got to, how fast, and
     * which practices came up short.
     */
    private String courseReport(Plan plan, LocalDate date) {
        CourseService.Standing s = courses.standing(plan, date);
        ProgressResponse progress = courses.progress(plan, date);
        LocalDate first = PlanSchedule.firstDay(plan);
        int total = plan.getTotalDays();
        long daysDone = s.days().stream().filter(s::isComplete).count();
        StringBuilder out = new StringBuilder();

        out.append("Here's how my plan is actually going. Read it and adjust the rest of the plan.\n\n");
        out.append("PLAN\n");
        out.append("- Title: ").append(plan.getTitle()).append('\n');
        out.append("- Length: ").append(total).append(" days, worked through in order, started ")
                .append(first.format(DAY)).append('\n');
        out.append("- Today: ").append(date.format(DAY));
        if (date.isBefore(first)) {
            out.append(" — not started yet");
        } else if (s.current() == null) {
            out.append(" — the whole course is finished");
        } else {
            out.append(" — on day ").append(s.current().getDayNumber()).append(" of ").append(total);
        }
        out.append("\n\n");

        out.append("PROGRESS\n");
        out.append("- ").append(daysDone).append(" of ").append(total).append(" days finished (")
                .append(progress.totalItems() == 0 ? 0 : Math.round(100f * progress.completed() / progress.totalItems()))
                .append("% of the whole course)\n");
        if (!date.isBefore(first)) {
            long elapsed = java.time.temporal.ChronoUnit.DAYS.between(first, date) + 1;
            out.append("- Pace: ").append(daysDone).append(daysDone == 1 ? " day" : " days")
                    .append(" finished in ").append(elapsed)
                    .append(elapsed == 1 ? " calendar day\n" : " calendar days\n");
        }
        out.append("- Current streak: ").append(progress.streak())
                .append(progress.streak() == 1 ? " day\n" : " days in a row\n");
        out.append("- Week by week (done/planned): ").append(weekLine(progress.weeks())).append("\n\n");

        List<String> shortfalls = new ArrayList<>();
        for (PlanDay day : s.days()) {
            for (PlanItem item : day.getItems()) {
                Completion c = s.done().get(item.getId());
                Integer planned = item.reps().value();
                if (c != null && c.getActualReps() != null && planned != null && c.getActualReps() < planned) {
                    shortfalls.add(item.getName() + " on Day " + day.getDayNumber() + ": plan said "
                            + amount(item, planned) + ", I managed " + amount(item, c.getActualReps()));
                }
            }
        }
        out.append("WHAT DIDN'T GO TO PLAN\n");
        if (shortfalls.isEmpty()) {
            out.append("- Nothing logged below the plan so far.\n");
        } else {
            shortfalls.forEach(line -> out.append("- ").append(line).append('\n'));
        }

        out.append("\nWHAT I WANT\n");
        out.append("Adjust the remaining days based on this — keep what's working, ");
        out.append("ease off what keeps coming up short, and keep it day by day.\n");
        out.append("Send the WHOLE updated plan again in the Anything format ");
        out.append("(day-by-day shape: --- header with anything/title/category/days, ");
        out.append("## Days A-B: Name, ### Day N: Title, a sentence of instructions, ");
        out.append("and \"- Practice | 10m | note: ...\").\n");
        return out.toString();
    }

    /** "5 min" for a timed practice, "12" for a count. */
    private static String amount(PlanItem item, int value) {
        if (item.reps().kind() == com.chintu.anything.parser.Reps.Kind.SECONDS) {
            return value % 60 == 0 ? (value / 60) + " min" : value + "s";
        }
        return String.valueOf(value);
    }

    private static String weekLine(List<WeekBar> weeks) {
        List<String> parts = new ArrayList<>();
        for (WeekBar w : weeks) {
            if (w.scheduled() > 0) {
                parts.add("W" + w.week() + " " + w.completed() + "/" + w.scheduled());
            }
        }
        return String.join(", ", parts);
    }

    /** Skipped days, unfinished past days, and exercises logged below the plan. */
    private List<String> shortfalls(Plan plan, LocalDate date) {
        LocalDate first = PlanSchedule.firstDay(plan);
        LocalDate last = date.isAfter(plan.endDate()) ? plan.endDate() : date;

        Map<LocalDate, List<Completion>> byDate = new HashMap<>();
        for (Completion c : completions.findByPlanIdAndDoneOnBetween(plan.getId(), first, last)) {
            byDate.computeIfAbsent(c.getDoneOn(), d -> new ArrayList<>()).add(c);
        }
        Set<LocalDate> skipped = new HashSet<>();
        skips.findByPlanId(plan.getId()).forEach(s -> skipped.add(s.getSkipOn()));

        List<String> lines = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            final LocalDate d0 = d;
            Optional<PlanDay> day = PlanSchedule.dayOn(plan, d);
            if (day.isEmpty()) {
                continue;
            }
            String title = day.get().getTitle();
            if (skipped.contains(d)) {
                // Say what was actually done before it was written off: an AI told only
                // "skipped" will rewrite a session that was three quarters finished.
                int did = (int) day.get().getItems().stream()
                        .filter(i -> byDate.getOrDefault(d0, List.<Completion>of()).stream()
                                .anyMatch(c -> c.getItem().getId().equals(i.getId())))
                        .count();
                int of = day.get().getItems().size();
                lines.add(title + " on " + d.format(DAY) + (did == 0
                        ? ": skipped"
                        : ": skipped after " + did + " of " + of + " exercises"));
                continue;
            }

            List<Completion> done = byDate.getOrDefault(d, List.of());
            Set<Long> doneIds = new HashSet<>();
            done.forEach(c -> doneIds.add(c.getItem().getId()));

            int total = day.get().getItems().size();
            int count = (int) day.get().getItems().stream().filter(i -> doneIds.contains(i.getId())).count();
            if (count < total && d.isBefore(date)) {
                lines.add(title + " on " + d.format(DAY) + ": only " + count + " of " + total + " exercises done");
            }

            // Exercises where the logged number was below what the plan asked for.
            for (Completion c : done) {
                Integer actual = c.getActualReps();
                PlanItem item = day.get().getItems().stream()
                        .filter(i -> i.getId().equals(c.getItem().getId()))
                        .findFirst()
                        .orElse(null);
                if (actual != null && item != null) {
                    Integer planned = item.reps().value();
                    if (planned != null && actual < planned) {
                        lines.add(item.getName() + " on " + d.format(DAY) + ": plan said "
                                + item.getSets() + "x" + planned + ", I managed " + actual);
                    }
                }
            }
        }
        return lines;
    }
}
