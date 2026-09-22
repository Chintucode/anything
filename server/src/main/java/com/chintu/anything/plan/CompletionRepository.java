package com.chintu.anything.plan;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CompletionRepository extends JpaRepository<Completion, Long> {

    Optional<Completion> findByItemIdAndDoneOn(Long itemId, LocalDate doneOn);

    /** Every completion for a plan within a date range (inclusive). */
    List<Completion> findByPlanIdAndDoneOnBetween(Long planId, LocalDate from, LocalDate to);
}
