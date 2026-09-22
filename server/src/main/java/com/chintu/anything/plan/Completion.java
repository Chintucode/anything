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

/** "This exercise was done on this date." One row per item per day at most. */
@Entity
@Table(name = "completions")
public class Completion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private PlanItem item;

    @Column(name = "done_on", nullable = false)
    private LocalDate doneOn;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Completion() {
    }

    public Completion(Plan plan, PlanItem item, LocalDate doneOn) {
        this.plan = plan;
        this.item = item;
        this.doneOn = doneOn;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Plan getPlan() { return plan; }
    public PlanItem getItem() { return item; }
    public LocalDate getDoneOn() { return doneOn; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
