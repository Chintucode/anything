package com.chintu.anything.parser;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Whole-plan checks that need every phase in view:
 * <ul>
 *   <li>phases cover 1 to the last week (or day) with no gaps or overlaps</li>
 *   <li>no day is written twice</li>
 * </ul>
 * Line-by-line checks live in {@link PlanParser}.
 */
final class PlanValidator {

    private PlanValidator() {
    }

    /**
     * Reports each day written twice.
     *
     * <p>The scope differs by schedule, and the difference isn't arbitrary: a weekday
     * is only unique inside its phase, because Monday comes round again in the next
     * one. A day number is unique across the whole plan — there is only one Day 9.
     */
    static void checkDuplicateDays(List<ParsedPhase> phases, List<ParseError> errors) {
        Map<Integer, ParsedDay> seenNumbers = new HashMap<>();

        for (ParsedPhase phase : phases) {
            Map<DayOfWeek, ParsedDay> seenWeekdays = new EnumMap<>(DayOfWeek.class);

            for (ParsedDay day : phase.days()) {
                ParsedDay first = day.dayNumber() != null
                        ? seenNumbers.putIfAbsent(day.dayNumber(), day)
                        : seenWeekdays.putIfAbsent(day.weekday(), day);

                if (first == null) {
                    continue;
                }
                String name = day.label();
                errors.add(new ParseError(day.line(), day.dayNumber() != null
                        ? name + " appears twice (first on line " + first.line()
                                + "). Put everything for " + name + " under one heading."
                        : name + " appears twice in this phase (first on line " + first.line()
                                + "). Put all of " + name + "'s exercises under one heading."));
            }
        }
    }

    /**
     * Checks that the phases cover 1..length exactly once, counted in the plan's own
     * unit — weeks for a weekly program, days for a sequential course.
     *
     * @return the phases sorted by where they start
     */
    static List<ParsedPhase> checkCoverage(PlanHeader header, List<ParsedPhase> phases, List<ParseError> errors) {
        List<ParsedPhase> sorted = new ArrayList<>(phases);
        sorted.sort(Comparator.comparingInt(ParsedPhase::from));

        String unit = header.unit();
        int next = 1;                  // first week (or day) not yet covered
        ParsedPhase previous = null;

        for (ParsedPhase phase : sorted) {
            if (phase.from() > next) {
                errors.add(new ParseError(phase.line(),
                        range(unit, next, phase.from() - 1) + " not covered by any phase."));
            } else if (phase.from() < next && previous != null) {
                errors.add(new ParseError(phase.line(),
                        capitalise(unit) + "s " + phase.from() + "-" + phase.to()
                                + " overlap with the phase on line " + previous.line() + "."));
            }
            next = Math.max(next, phase.to() + 1);
            previous = phase;
        }

        if (previous != null && next <= header.length()) {
            errors.add(new ParseError(previous.line(),
                    range(unit, next, header.length()) + " not covered by any phase. The plan has "
                            + header.length() + " " + unit + "s."));
        }
        return sorted;
    }

    private static String range(String unit, int from, int to) {
        return from == to
                ? capitalise(unit) + " " + from + " is"
                : capitalise(unit) + "s " + from + "-" + to + " are";
    }

    private static String capitalise(String word) {
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}
