package com.chintu.anything.plan;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** "This training day was skipped." It stops counting, instead of counting as failure. */
@Entity
@Table(name = "skipped_days")
public class SkippedDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "skip_on", nullable = false)
    private LocalDate skipOn;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected SkippedDay() {
    }

    public SkippedDay(Plan plan, LocalDate skipOn) {
        this.plan = plan;
        this.skipOn = skipOn;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Plan getPlan() { return plan; }
    public LocalDate getSkipOn() { return skipOn; }
}
