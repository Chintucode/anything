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

/**
 * "I took this rest day."
 *
 * <p>Nothing is due on a rest day, so this changes no percentage. It exists because
 * a plan you're following should never have a day with nothing to do on it: resting
 * when the plan says rest is following the plan, and it should feel like it.
 */
@Entity
@Table(name = "rested_days")
public class RestedDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "rested_on", nullable = false)
    private LocalDate restedOn;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected RestedDay() {
    }

    public RestedDay(Plan plan, LocalDate restedOn) {
        this.plan = plan;
        this.restedOn = restedOn;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Plan getPlan() { return plan; }
    public LocalDate getRestedOn() { return restedOn; }
}
