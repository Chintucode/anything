package com.chintu.anything.parser;

import java.util.List;

/** Everything the parser understood from a plan. */
public record ParsedPlan(PlanHeader header, List<ParsedPhase> phases) {

    public ParsedPlan {
        phases = List.copyOf(phases);
    }
}
