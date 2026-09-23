package com.chintu.anything.plan;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.chintu.anything.plan.TodayResponse.Status;
import com.chintu.anything.plan.WeekResponse.WeekDay;
import com.chintu.anything.schedule.PlanCalendar;
import com.chintu.anything.schedule.PlanCalendar.Position;

/** The Monday-to-Sunday week around a date, with each day's state and counts. */
@Service
public class WeekService {

    private final PlanRepository plans;
    private final CompletionRepository completions;
    private final SkippedDayRepository skips;

    public WeekService(PlanRepository plans, CompletionRepository completions, SkippedDayRepository skips) {
        this.plans = plans;
        this.completions = completions;
        this.skips = skips;
    }

    @Transactional(readOnly = true)
    public WeekResponse week(long planId, LocalDate date) {
        Plan plan = plans.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan " + planId + " not found."));

        LocalDate monday = date.minusDays(date.getDayOfWeek().getValue() - 1L);
        LocalDate sunday = monday.plusDays(6);

        Map<LocalDate, Set<Long>> doneByDate = new HashMap<>();
        for (Completion c : completions.findByPlanIdAndDoneOnBetween(plan.getId(), monday, sunday)) {
            doneByDate.computeIfAbsent(c.getDoneOn(), d -> new HashSet<>()).add(c.getItem().getId());
        }
        Set<LocalDate> skipped = new HashSet<>();
        for (SkippedDay s : skips.findByPlanId(plan.getId())) {
            skipped.add(s.getSkipOn());
        }

        List<WeekDay> days = new ArrayList<>();
        for (LocalDate d = monday; !d.isAfter(sunday); d = d.plusDays(1)) {
            days.add(dayOf(plan, d, doneByDate.get(d), skipped.contains(d)));
        }
        return new WeekResponse(plan.getId(), monday, sunday, days);
    }

    private static WeekDay dayOf(Plan plan, LocalDate date, Set<Long> doneIds, boolean isSkipped) {
        Position pos = PlanSchedule.locate(plan, date);
        DayOfWeek weekday = date.getDayOfWeek();

        if (pos.status() == PlanCalendar.Status.NOT_STARTED) {
            return new WeekDay(date, weekday, Status.NOT_STARTED, null, null, 0, 0);
        }
        if (pos.status() == PlanCalendar.Status.FINISHED) {
            return new WeekDay(date, weekday, Status.FINISHED, null, null, 0, 0);
        }

        Optional<PlanDay> day = PlanSchedule.dayAt(plan, pos);
        if (day.isEmpty()) {
            return new WeekDay(date, weekday, Status.REST, pos.week(), null, 0, 0);
        }

        int total = day.get().getItems().size();
        int done = doneIds == null ? 0 : (int) day.get().getItems().stream()
                .filter(i -> doneIds.contains(i.getId()))
                .count();
        Status status = isSkipped ? Status.SKIPPED : Status.TRAINING;
        return new WeekDay(date, weekday, status, pos.week(), day.get().getTitle(), done, total);
    }
}
