package com.chintu.anything.plan;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    /** Newest plans first, for the plans list. */
    List<Plan> findAllByOrderByCreatedAtDesc();
}
