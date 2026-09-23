package com.chintu.anything.plan;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RestedDayRepository extends JpaRepository<RestedDay, Long> {

    Optional<RestedDay> findByPlanIdAndRestedOn(Long planId, LocalDate restedOn);

    boolean existsByPlanIdAndRestedOn(Long planId, LocalDate restedOn);
}
