package com.chintu.anything.parser;

import java.util.List;

/**
 * A "## Weeks A-B: Name" section and its training days.
 *
 * @param line the heading's line number, kept for later validation messages
 */
public record ParsedPhase(int fromWeek, int toWeek, String name, int line, List<ParsedDay> days) {

    public ParsedPhase {
        days = List.copyOf(days);
    }
}
