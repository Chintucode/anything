package com.chintu.anything.plan;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.chintu.anything.parser.PlanHeader.Schedule;
import com.chintu.anything.plan.CompletionService.CompletionResponse;
import com.chintu.anything.plan.ProgressResponse.WeekBar;
import com.chintu.anything.plan.TodayResponse.NextWorkout;
import com.chintu.anything.plan.TodayResponse.Status;
import com.chintu.anything.plan.TodayResponse.TodayItem;

/**
 * Runs day-by-day plans: "Day 1, Day 2, Day 3", worked through in order.
 *
 * <p>A weekly program is pinned to the calendar. Monday is a date, and if you miss
 * it, it's gone — hence Skip and Shift. A course is pinned to <em>order</em>. Asked for
 * a 21-day meditation plan, three different AI models all told the reader the same
 * thing: "Miss a day? Just resume the next day. Don't restart, don't double up."
 * So here:
 *
 * <ul>
 *   <li><b>Today</b> is the day you worked on today, or, if you haven't started yet
 *       today, the lowest-numbered day you haven't finished. Nothing is ever missed;
 *       an unfinished day simply waits for you.</li>
 *   <li><b>Finishing a day</b> shows it as done for the rest of the date, with the next
 *       one offered for tomorrow. It doesn't jump ahead — no doubling up.</li>
 *   <li><b>An item is done once</b>, on whatever date that happened. Completions after
 *       the date being asked about are ignored, so any date reads as it did then.</li>
 * </ul>
 *
 * <p>The weekly services hand a plan over to this one when it's a course. Nothing
 * here touches how weekly plans behave.
 */
@Service
public class CourseService {

    private final CompletionRepository completions;

    public CourseService(CompletionRepository completions) {
        this.completions = completions;
    }

    // ---------------------------------------------------------------- where it stands

    /**
     * Where a course stands on a date.
     *
     * @param days     every day of the course, in order
     * @param done     item id to its completion, counting only completions on or before the date
     * @param current  the day to show; null before the start and once everything is finished
     * @param nextToDo the first day still to do after the current one; null if none
     */
    record Standing(List<PlanDay> days, Map<Long, Completion> done, PlanDay current, PlanDay nextToDo) {

        boolean isComplete(PlanDay day) {
            return !day.getItems().isEmpty()
                    && day.getItems().stream().allMatch(i -> done.containsKey(i.getId()));
        }

        int doneCount(PlanDay day) {
            return (int) day.getItems().stream().filter(i -> done.containsKey(i.getId())).count();
        }
    }

    Standing standing(Plan plan, LocalDate date) {
        List<PlanDay> days = plan.getPhases().stream()
                .flatMap(p -> p.getDays().stream())
                .sorted(Comparator.comparing(PlanDay::getDayNumber))
                .toList();

        Map<Long, Completion> done = new HashMap<>();
        for (Completion c : completions.findByPlanId(plan.getId())) {
            if (!c.getDoneOn().isAfter(date)) {
                done.merge(c.getItem().getId(), c, (a, b) -> a.getDoneOn().isAfter(b.getDoneOn()) ? b : a);
            }
        }

        Standing partial = new Standing(days, done, null, null);
        PlanDay firstUnfinished = days.stream().filter(d -> !partial.isComplete(d)).findFirst().orElse(null);

        // The day touched today wins: finishing Day 9 this morning keeps Day 9 on screen
        // (done, with Day 10 offered for tomorrow) instead of jumping straight to Day 10.
        PlanDay workedToday = null;
        for (PlanDay d : days) {
            boolean touchedToday = d.getItems().stream()
                    .map(i -> done.get(i.getId()))
                    .anyMatch(c -> c != null && c.getDoneOn().isEqual(date));
            if (touchedToday) {
                workedToday = d;
            }
        }

        PlanDay current = workedToday != null ? workedToday : firstUnfinished;
        PlanDay nextToDo = null;
        if (current != null) {
            int at = days.indexOf(current);
            for (int i = at + 1; i < days.size(); i++) {
                if (!partial.isComplete(days.get(i))) {
                    nextToDo = days.get(i);
                    break;
                }
            }
        }
        return new Standing(days, done, current, nextToDo);
    }

    // ---------------------------------------------------------------- today

    TodayResponse today(Plan plan, LocalDate date) {
        Standing s = standing(plan, date);
        LocalDate firstDay = PlanSchedule.firstDay(plan);
        int totalDays = plan.getTotalDays();

        if (date.isBefore(firstDay)) {
            PlanDay first = s.days().isEmpty() ? null : s.days().get(0);
            NextWorkout next = first == null ? null
                    : new NextWorkout(firstDay, firstDay.getDayOfWeek(), first.getTitle());
            return response(plan, date, Status.NOT_STARTED, null, ChronoUnit.DAYS.between(date, firstDay),
                    next, s, totalDays);
        }
        if (s.current() == null) {
            return response(plan, date, Status.FINISHED, null, null, null, s, totalDays);
        }

        PlanDay day = s.current();
        NextWorkout next = null;
        if (s.isComplete(day) && s.nextToDo() != null) {
            LocalDate tomorrow = date.plusDays(1);
            next = new NextWorkout(tomorrow, tomorrow.getDayOfWeek(), s.nextToDo().getTitle());
        }
        return response(plan, date, Status.TRAINING, day, null, next, s, totalDays);
    }

    private TodayResponse response(Plan plan, LocalDate date, Status status, PlanDay day,
            Long daysUntilStart, NextWorkout next, Standing s, int totalDays) {

        List<TodayItem> items = null;
        Integer doneCount = null;
        Integer week = null;
        String phaseName = null;
        String description = null;

        if (day != null) {
            items = day.getItems().stream()
                    .map(i -> {
                        Completion c = s.done().get(i.getId());
                        return new TodayItem(i.getId(), i.getName(), i.getSets(), i.reps(), i.getRestSeconds(),
                                i.getNote(), c != null, c != null ? c.getActualReps() : null);
                    })
                    .toList();
            doneCount = s.doneCount(day);
            week = weekOf(day.getDayNumber());
            phaseName = day.getPhase().getName();
            description = day.getDescription().isEmpty() ? null : day.getDescription();
        }

        return new TodayResponse(
                plan.getId(),
                plan.getTitle(),
                status,
                date,
                date.getDayOfWeek(),
                week,
                plan.getWeeks(),
                phaseName,
                day != null ? day.getTitle() : null,
                items,
                doneCount,
                daysUntilStart,
                next,
                null,              // a course can't be missed
                null,              // and has no rest days to mark
                Schedule.SEQUENTIAL,
                day != null ? day.getDayNumber() : null,
                totalDays,
                description);
    }

    /** Days 1-7 are week 1, days 8-14 week 2: the chunks the bars are drawn in. */
    private static int weekOf(int dayNumber) {
        return (dayNumber - 1) / 7 + 1;
    }

    // ---------------------------------------------------------------- progress

    ProgressResponse progress(Plan plan, LocalDate date) {
        Standing s = standing(plan, date);
        int weeks = plan.getWeeks();

        int[] weekScheduled = new int[weeks + 1];
        int[] weekCompleted = new int[weeks + 1];
        int totalItems = 0;
        int completed = 0;
        int dueSoFar = 0;

        boolean started = !date.isBefore(PlanSchedule.firstDay(plan));
        // "Due" in a course means up to the day you're on, not up to a date.
        int reached = !started ? 0
                : s.current() != null ? s.current().getDayNumber()
                : Integer.MAX_VALUE;

        for (PlanDay day : s.days()) {
            int due = day.getItems().size();
            int done = s.doneCount(day);
            int w = Math.min(weekOf(day.getDayNumber()), weeks);
            weekScheduled[w] += due;
            weekCompleted[w] += done;
            totalItems += due;
            completed += done;
            if (day.getDayNumber() <= reached) {
                dueSoFar += due;
            }
        }

        List<WeekBar> bars = new ArrayList<>();
        for (int w = 1; w <= weeks; w++) {
            bars.add(new WeekBar(w, weekScheduled[w], weekCompleted[w]));
        }
        int percent = dueSoFar == 0 ? 0 : Math.round(100f * completed / dueSoFar);
        return new ProgressResponse(plan.getId(), date, percent, completed, dueSoFar,
                streak(s, date), totalItems, bars);
    }

    /**
     * Calendar days in a row on which a day of the course was finished, counting back
     * from today — or from yesterday, if nothing is finished yet today, because today
     * isn't over.
     */
    private static int streak(Standing s, LocalDate date) {
        Set<LocalDate> finishedOn = new HashSet<>();
        for (PlanDay day : s.days()) {
            if (s.isComplete(day)) {
                day.getItems().stream()
                        .map(i -> s.done().get(i.getId()).getDoneOn())
                        .max(LocalDate::compareTo)
                        .ifPresent(finishedOn::add);
            }
        }
        LocalDate d = finishedOn.contains(date) ? date : date.minusDays(1);
        int streak = 0;
        while (finishedOn.contains(d)) {
            streak++;
            d = d.minusDays(1);
        }
        return streak;
    }

    // ---------------------------------------------------------------- ticking

    /**
     * Ticks or unticks an item. It's done once, whenever that was: unticking removes it
     * whatever date it was ticked on, which a date-keyed lookup would miss if you ticked
     * half of Day 3 yesterday and untick it today.
     */
    CompletionResponse setCompletion(Plan plan, CompletionRequest request) {
        PlanItem item = plan.getPhases().stream()
                .flatMap(p -> p.getDays().stream())
                .flatMap(d -> d.getItems().stream())
                .filter(i -> i.getId().equals(request.itemId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "That item isn't part of this plan."));

        List<Completion> existing = completions.findByItemId(item.getId());
        if (request.done()) {
            Completion completion = existing.isEmpty()
                    ? completions.save(new Completion(plan, item, request.date()))
                    : existing.get(0);
            completion.setActualReps(request.actualReps());
            return new CompletionResponse(item.getId(), completion.getDoneOn(), true, completion.getActualReps());
        }
        completions.deleteAll(existing);
        return new CompletionResponse(item.getId(), request.date(), false, null);
    }
}
