package com.chintu.anything.plan;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.chintu.anything.parser.PlanHeader.Schedule;

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

    /** WEEKLY (pinned to weekdays) or SEQUENTIAL (worked through in order, Day 1, Day 2...). */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private Schedule schedule = Schedule.WEEKLY;

    /** Day-by-day plans only: how many days long. Weekly plans measure themselves in {@link #weeks}. */
    @Column(name = "total_days")
    private Integer totalDays;

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

    /** A day-by-day course, {@code days} long. */
    public static Plan course(String title, String category, int days, LocalDate startDate, String rawMarkdown) {
        Plan plan = new Plan(title, category, (days + 6) / 7, startDate, rawMarkdown);
        plan.schedule = Schedule.SEQUENTIAL;
        plan.totalDays = days;
        return plan;
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

    /**
     * Last calendar day of the plan, including any days it was shifted.
     *
     * <p>For a day-by-day course this is the earliest it can finish — one day a day,
     * nothing missed. It finishes later if days are missed, and that's by design: a
     * course waits for you rather than marking you absent.
     */
    public LocalDate endDate() {
        long length = isSequential() ? totalDays : (long) weeks * 7;
        return startDate.plusDays(length - 1 + dayOffset);
    }

    public boolean isSequential() {
        return schedule == Schedule.SEQUENTIAL;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public int getWeeks() { return weeks; }
    public Schedule getSchedule() { return schedule; }
    public Integer getTotalDays() { return totalDays; }
    public LocalDate getStartDate() { return startDate; }
    public int getDayOffset() { return dayOffset; }
    public String getRawMarkdown() { return rawMarkdown; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<PlanPhase> getPhases() { return phases; }
}
