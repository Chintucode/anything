package com.chintu.anything.parser;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Whole-plan checks that need every phase in view:
 * <ul>
 *   <li>phases cover week 1 to the last week with no gaps or overlaps</li>
 *   <li>no weekday appears twice inside one phase</li>
 * </ul>
 * Line-by-line checks live in {@link PlanParser}.
 */
final class PlanValidator {

    private PlanValidator() {
    }

    /** Reports each weekday that appears more than once in the same phase. */
    static void checkDuplicateDays(List<ParsedPhase> phases, List<ParseError> errors) {
        for (ParsedPhase phase : phases) {
            Map<DayOfWeek, ParsedDay> seen = new EnumMap<>(DayOfWeek.class);
            for (ParsedDay day : phase.days()) {
                ParsedDay first = seen.putIfAbsent(day.weekday(), day);
                if (first != null) {
                    String name = displayName(day.weekday());
                    errors.add(new ParseError(day.line(),
                            name + " appears twice in this phase (first on line " + first.line()
                                    + "). Put all of " + name + "'s exercises under one heading."));
                }
            }
        }
    }

    /**
     * Checks that the phases cover weeks 1..weeks exactly once.
     *
     * @return the phases sorted by starting week
     */
    static List<ParsedPhase> checkWeekCoverage(PlanHeader header, List<ParsedPhase> phases, List<ParseError> errors) {
        List<ParsedPhase> sorted = new ArrayList<>(phases);
        sorted.sort(Comparator.comparingInt(ParsedPhase::fromWeek));

        int nextWeek = 1;              // first week not yet covered
        ParsedPhase previous = null;

        for (ParsedPhase phase : sorted) {
            if (phase.fromWeek() > nextWeek) {
                errors.add(new ParseError(phase.line(),
                        weekRange(nextWeek, phase.fromWeek() - 1) + " not covered by any phase."));
            } else if (phase.fromWeek() < nextWeek && previous != null) {
                errors.add(new ParseError(phase.line(),
                        "Weeks " + phase.fromWeek() + "-" + phase.toWeek()
                                + " overlap with the phase on line " + previous.line() + "."));
            }
            nextWeek = Math.max(nextWeek, phase.toWeek() + 1);
            previous = phase;
        }

        if (previous != null && nextWeek <= header.weeks()) {
            errors.add(new ParseError(previous.line(),
                    weekRange(nextWeek, header.weeks()) + " not covered by any phase. The plan has "
                            + header.weeks() + " weeks."));
        }
        return sorted;
    }

    private static String weekRange(int from, int to) {
        return from == to ? "Week " + from + " is" : "Weeks " + from + "-" + to + " are";
    }

    private static String displayName(DayOfWeek day) {
        String lower = day.name().toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
