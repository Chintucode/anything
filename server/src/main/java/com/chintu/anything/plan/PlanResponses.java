package com.chintu.anything.plan;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.chintu.anything.parser.Reps;

/**
 * JSON shapes returned by the plan endpoints. Entities are never returned directly,
 * so the database structure can change without breaking the web app.
 */
public final class PlanResponses {

    private PlanResponses() {
    }

    /** One row in the plans list. */
    public record PlanSummary(
            Long id, String title, String category, int weeks,
            LocalDate startDate, LocalDate endDate, LocalDateTime createdAt) {

        static PlanSummary from(Plan plan) {
            return new PlanSummary(plan.getId(), plan.getTitle(), plan.getCategory(), plan.getWeeks(),
                    plan.getStartDate(), plan.endDate(), plan.getCreatedAt());
        }
    }

    /** A plan with everything in it. */
    public record PlanDetail(
            Long id, String title, String category, int weeks,
            LocalDate startDate, LocalDate endDate, int dayOffset, LocalDateTime createdAt,
            List<PhaseView> phases) {

        static PlanDetail from(Plan plan) {
            return new PlanDetail(plan.getId(), plan.getTitle(), plan.getCategory(), plan.getWeeks(),
                    plan.getStartDate(), plan.endDate(), plan.getDayOffset(), plan.getCreatedAt(),
                    plan.getPhases().stream().map(PhaseView::from).toList());
        }
    }

    public record PhaseView(Long id, int fromWeek, int toWeek, String name, List<DayView> days) {

        static PhaseView from(PlanPhase phase) {
            return new PhaseView(phase.getId(), phase.getFromWeek(), phase.getToWeek(), phase.getName(),
                    phase.getDays().stream().map(DayView::from).toList());
        }
    }

    public record DayView(Long id, DayOfWeek weekday, String title, List<ItemView> items) {

        static DayView from(PlanDay day) {
            return new DayView(day.getId(), day.getWeekday(), day.getTitle(),
                    day.getItems().stream().map(ItemView::from).toList());
        }
    }

    public record ItemView(Long id, String name, int sets, Reps reps, Integer restSeconds, String note) {

        static ItemView from(PlanItem item) {
            return new ItemView(item.getId(), item.getName(), item.getSets(), item.reps(),
                    item.getRestSeconds(), item.getNote());
        }
    }
}
