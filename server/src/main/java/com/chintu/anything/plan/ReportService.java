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

    public ReportService(PlanRepository plans, CompletionRepository completions,
            SkippedDayRepository skips, ProgressService progressService) {
        this.plans = plans;
        this.completions = completions;
        this.skips = skips;
        this.progressService = progressService;
    }

    @Transactional(readOnly = true)
    public String report(long planId, LocalDate date) {
        Plan plan = plans.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan " + planId + " not found."));

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
        out.append("- ").append(progress.completed()).append(" of ").append(progress.scheduled())
                .append(" scheduled exercises done so far (").append(progress.percent()).append("%)\n");
        out.append("- Current streak: ").append(progress.streak())
                .append(progress.streak() == 1 ? " training day\n" : " training days\n");
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
            Optional<PlanDay> day = PlanSchedule.dayOn(plan, d);
            if (day.isEmpty()) {
                continue;
            }
            String title = day.get().getTitle();
            if (skipped.contains(d)) {
                lines.add(title + " on " + d.format(DAY) + ": skipped");
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
