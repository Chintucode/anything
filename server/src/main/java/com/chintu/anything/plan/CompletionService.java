package com.chintu.anything.plan;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ticks and unticks exercises.
 *
 * <p>PUT is idempotent: sending "done: true" twice leaves one completion, and
 * "done: false" on something never ticked is fine. Double taps and retries on a
 * flaky phone connection can't corrupt the data.
 */
@Service
public class CompletionService {

    private final PlanRepository plans;
    private final CompletionRepository completions;

    public CompletionService(PlanRepository plans, CompletionRepository completions) {
        this.plans = plans;
        this.completions = completions;
    }

    @Transactional
    public CompletionResponse set(long planId, CompletionRequest request) {
        Plan plan = plans.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan " + planId + " not found."));
        LocalDate date = request.date();

        // The item must be part of this plan AND scheduled on that date.
        PlanItem item = PlanSchedule.dayOn(plan, date)
                .flatMap(day -> day.getItems().stream()
                        .filter(i -> i.getId().equals(request.itemId()))
                        .findFirst())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "That exercise isn't scheduled on " + date + "."));

        var existing = completions.findByItemIdAndDoneOn(item.getId(), date);
        if (request.done()) {
            Completion completion = existing.orElseGet(() -> completions.save(new Completion(plan, item, date)));
            completion.setActualReps(request.actualReps());   // null clears it again
            return new CompletionResponse(item.getId(), date, true, completion.getActualReps());
        }
        existing.ifPresent(completions::delete);
        return new CompletionResponse(item.getId(), date, false, null);
    }

    public record CompletionResponse(Long itemId, LocalDate date, boolean done, Integer actualReps) {
    }
}
