package com.chintu.anything.plan;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** A saved plan. Its phases, days and items are saved and deleted with it. */
@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private int weeks;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "day_offset", nullable = false)
    private int dayOffset;

    @Column(name = "raw_markdown", nullable = false, length = 100_000)
    private String rawMarkdown;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fromWeek ASC")
    private List<PlanPhase> phases = new ArrayList<>();

    protected Plan() {
        // for JPA
    }

    public Plan(String title, String category, int weeks, LocalDate startDate, String rawMarkdown) {
        this.title = title;
        this.category = category;
        this.weeks = weeks;
        this.startDate = startDate;
        this.rawMarkdown = rawMarkdown;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** Adds a phase and sets the back-reference, keeping both sides in sync. */
    public void addPhase(PlanPhase phase) {
        phases.add(phase);
        phase.setPlan(this);
    }

    /** Moves the whole plan later (or earlier, with a negative number). */
    public void shiftBy(int days) {
        dayOffset += days;
    }

    /** Last calendar day of the plan, including any days it was shifted. */
    public LocalDate endDate() {
        return startDate.plusDays((long) weeks * 7 - 1 + dayOffset);
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public int getWeeks() { return weeks; }
    public LocalDate getStartDate() { return startDate; }
    public int getDayOffset() { return dayOffset; }
    public String getRawMarkdown() { return rawMarkdown; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<PlanPhase> getPhases() { return phases; }
}
