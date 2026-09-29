package com.chintu.anything.plan;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.chintu.anything.parser.PlanHeader.Schedule;

import com.chintu.anything.parser.Reps;

/**
 * JSON shapes returned by the plan endpoints. Entities are never returned directly,
 * so the database structure can change without breaking the web app.
 */
public final class PlanResponses {

    private PlanResponses() {
    }

    /** One row in the plans list. */
    /**
     * @param schedule  WEEKLY or SEQUENTIAL
     * @param totalDays day-by-day plans only; null for a weekly plan
     * @param endDate   for a day-by-day plan, the earliest it can finish
     */
    public record PlanSummary(
            Long id, String title, String category, int weeks,
            LocalDate startDate, LocalDate endDate, LocalDateTime createdAt,
            Schedule schedule, Integer totalDays) {

        static PlanSummary from(Plan plan) {
            return new PlanSummary(plan.getId(), plan.getTitle(), plan.getCategory(), plan.getWeeks(),
                    plan.getStartDate(), plan.endDate(), plan.getCreatedAt(),
                    plan.getSchedule(), plan.getTotalDays());
        }
    }

    /** A plan with everything in it. */
    public record PlanDetail(
            Long id, String title, String category, int weeks,
            LocalDate startDate, LocalDate endDate, int dayOffset, LocalDateTime createdAt,
            Schedule schedule, Integer totalDays, List<PhaseView> phases) {

        static PlanDetail from(Plan plan) {
            return new PlanDetail(plan.getId(), plan.getTitle(), plan.getCategory(), plan.getWeeks(),
                    plan.getStartDate(), plan.endDate(), plan.getDayOffset(), plan.getCreatedAt(),
                    plan.getSchedule(), plan.getTotalDays(),
                    plan.getPhases().stream().map(PhaseView::from).toList());
        }
    }

    public record PhaseView(Long id, int fromWeek, int toWeek, String name, List<DayView> days) {

        static PhaseView from(PlanPhase phase) {
            return new PhaseView(phase.getId(), phase.getFromWeek(), phase.getToWeek(), phase.getName(),
                    phase.getDays().stream().map(DayView::from).toList());
        }
    }

    public record DayView(Long id, DayOfWeek weekday, Integer dayNumber, String title, String description,
            List<ItemView> items) {

        static DayView from(PlanDay day) {
            return new DayView(day.getId(), day.getWeekday(), day.getDayNumber(), day.getTitle(),
                    day.getDescription(), day.getItems().stream().map(ItemView::from).toList());
        }
    }

    public record ItemView(Long id, String name, int sets, Reps reps, Integer restSeconds, String note) {

        static ItemView from(PlanItem item) {
            return new ItemView(item.getId(), item.getName(), item.getSets(), item.reps(),
                    item.getRestSeconds(), item.getNote());
        }
    }
}
