package com.chintu.anything.plan;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SkippedDayRepository extends JpaRepository<SkippedDay, Long> {

    Optional<SkippedDay> findByPlanIdAndSkipOn(Long planId, LocalDate skipOn);

    List<SkippedDay> findByPlanId(Long planId);

    boolean existsByPlanIdAndSkipOn(Long planId, LocalDate skipOn);
}
