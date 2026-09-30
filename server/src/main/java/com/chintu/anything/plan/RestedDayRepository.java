package com.chintu.anything.plan;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RestedDayRepository extends JpaRepository<RestedDay, Long> {

    Optional<RestedDay> findByPlanIdAndRestedOn(Long planId, LocalDate restedOn);

    List<RestedDay> findByPlanId(Long planId);

    boolean existsByPlanIdAndRestedOn(Long planId, LocalDate restedOn);
}
