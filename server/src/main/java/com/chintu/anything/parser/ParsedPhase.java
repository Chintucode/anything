package com.chintu.anything.parser;

import java.util.List;

/**
 * A "## Weeks 1-4: Foundation" or "## Days 1-7: Showing Up" section and its days.
 *
 * <p>{@code from} and {@code to} are inclusive and counted in the plan's own unit —
 * weeks for a weekly plan, days for a sequential one.
 *
 * @param description the line under the heading, when the plan states the phase's goal
 * @param line        the heading's line number, kept for later validation messages
 */
public record ParsedPhase(int from, int to, String name, String description, int line, List<ParsedDay> days) {

    public ParsedPhase {
        days = List.copyOf(days);
    }
}
