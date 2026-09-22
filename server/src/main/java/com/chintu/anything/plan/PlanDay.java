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
    @Column(nullable = false, length = 9)
    private DayOfWeek weekday;

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

    public void addItem(PlanItem item) {
        items.add(item);
        item.setDay(this);
    }

    void setPhase(PlanPhase phase) { this.phase = phase; }

    public Long getId() { return id; }
    public PlanPhase getPhase() { return phase; }
    public DayOfWeek getWeekday() { return weekday; }
    public String getTitle() { return title; }
    public int getSortOrder() { return sortOrder; }
    public List<PlanItem> getItems() { return items; }
}
