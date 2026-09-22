package com.chintu.anything.plan;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.chintu.anything.parser.Reps;

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
import jakarta.persistence.Table;

/** One exercise in a day. The reps are stored flat (kind, value, detail, raw). */
@Entity
@Table(name = "plan_items")
public class PlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "day_id", nullable = false)
    private PlanDay day;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private String name;

    @Column(name = "set_count", nullable = false)
    private int sets;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reps_kind", nullable = false, length = 10)
    private Reps.Kind repsKind;

    @Column(name = "reps_value")
    private Integer repsValue;

    @Column(name = "reps_detail", nullable = false)
    private String repsDetail;

    @Column(name = "reps_raw", nullable = false)
    private String repsRaw;

    @Column(name = "rest_seconds")
    private Integer restSeconds;

    @Column(nullable = false)
    private String note;

    protected PlanItem() {
    }

    public PlanItem(int sortOrder, String name, int sets, Reps reps, Integer restSeconds, String note) {
        this.sortOrder = sortOrder;
        this.name = name;
        this.sets = sets;
        this.repsKind = reps.kind();
        this.repsValue = reps.value();
        this.repsDetail = reps.detail();
        this.repsRaw = reps.raw();
        this.restSeconds = restSeconds;
        this.note = note;
    }

    void setDay(PlanDay day) { this.day = day; }

    public Reps reps() {
        return new Reps(repsKind, repsValue, repsDetail, repsRaw);
    }

    public Long getId() { return id; }
    public PlanDay getDay() { return day; }
    public int getSortOrder() { return sortOrder; }
    public String getName() { return name; }
    public int getSets() { return sets; }
    public Integer getRestSeconds() { return restSeconds; }
    public String getNote() { return note; }
}
