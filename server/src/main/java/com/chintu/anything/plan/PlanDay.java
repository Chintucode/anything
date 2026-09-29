package com.chintu.anything.plan;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "plan_days")
public class PlanDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "phase_id", nullable = false)
    private PlanPhase phase;

    // Stored as text ("MONDAY"); the explicit VARCHAR stops Hibernate using a native enum type.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 9)
    private DayOfWeek weekday;

    /** Day-by-day plans only: the 9 in "Day 9". A day has a weekday or a number, never both. */
    @Column(name = "day_number")
    private Integer dayNumber;

    /** The paragraph under the heading. For a meditation day, the practice itself. */
    @Column(nullable = false, length = 1000)
    private String description = "";

    @Column(nullable = false)
    private String title;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<PlanItem> items = new ArrayList<>();

    protected PlanDay() {
    }

    public PlanDay(DayOfWeek weekday, String title, int sortOrder) {
        this.weekday = weekday;
        this.title = title;
        this.sortOrder = sortOrder;
    }

    /** "### Day 9: Naming Thoughts" — a day in a course, placed by number, not by weekday. */
    public static PlanDay numbered(int dayNumber, String title, int sortOrder) {
        PlanDay day = new PlanDay(null, title, sortOrder);
        day.dayNumber = dayNumber;
        return day;
    }

    public PlanDay describedAs(String description) {
        this.description = description == null ? "" : description;
        return this;
    }

    public void addItem(PlanItem item) {
        items.add(item);
        item.setDay(this);
    }

    void setPhase(PlanPhase phase) { this.phase = phase; }

    public Long getId() { return id; }
    public PlanPhase getPhase() { return phase; }
    public DayOfWeek getWeekday() { return weekday; }
    public Integer getDayNumber() { return dayNumber; }
    public String getDescription() { return description; }
    public String getTitle() { return title; }
    public int getSortOrder() { return sortOrder; }
    public List<PlanItem> getItems() { return items; }
}
