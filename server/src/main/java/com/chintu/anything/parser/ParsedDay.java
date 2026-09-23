package com.chintu.anything.parser;

import java.time.DayOfWeek;
import java.util.List;

/**
 * One day of a plan, with its items.
 *
 * <p>A day is identified one way or the other, never both: by {@code weekday} in a
 * weekly plan ("### Mon: Push"), or by {@code dayNumber} in a sequential one
 * ("### Day 9: Naming Thoughts"). The unused one is null.
 *
 * @param description the paragraph written under the heading, if any. On a workout day
 *                    that's a nicety; on a meditation day it's the entire instruction,
 *                    which is why it isn't squeezed into a note on an item.
 */
public record ParsedDay(DayOfWeek weekday, Integer dayNumber, String title, String description,
        int line, List<ParsedItem> items) {

    public ParsedDay {
        items = List.copyOf(items);
    }

    /** How this day reads in an error message: "Monday" or "Day 9". */
    public String label() {
        if (dayNumber != null) {
            return "Day " + dayNumber;
        }
        String lower = weekday.name().toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
