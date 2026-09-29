package com.chintu.anything.plan;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CompletionRepository extends JpaRepository<Completion, Long> {

    Optional<Completion> findByItemIdAndDoneOn(Long itemId, LocalDate doneOn);

    /** Every completion for a plan within a date range (inclusive). */
    List<Completion> findByPlanIdAndDoneOnBetween(Long planId, LocalDate from, LocalDate to);

    /** Every completion for a plan, whenever it happened. Day-by-day plans aren't tied to dates. */
    List<Completion> findByPlanId(Long planId);

    /** In a day-by-day plan an item is done once, on whatever date that happened. */
    List<Completion> findByItemId(Long itemId);
}
