package com.chintu.anything.plan;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "plan_phases")
public class PlanPhase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "from_week", nullable = false)
    private int fromWeek;

    @Column(name = "to_week", nullable = false)
    private int toWeek;

    @Column(nullable = false)
    private String name;

    @OneToMany(mappedBy = "phase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<PlanDay> days = new ArrayList<>();

    protected PlanPhase() {
    }

    public PlanPhase(int fromWeek, int toWeek, String name) {
        this.fromWeek = fromWeek;
        this.toWeek = toWeek;
        this.name = name;
    }

    public void addDay(PlanDay day) {
        days.add(day);
        day.setPhase(this);
    }

    void setPlan(Plan plan) { this.plan = plan; }

    public Long getId() { return id; }
    public Plan getPlan() { return plan; }
    public int getFromWeek() { return fromWeek; }
    public int getToWeek() { return toWeek; }
    public String getName() { return name; }
    public List<PlanDay> getDays() { return days; }
}
