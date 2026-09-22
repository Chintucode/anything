package com.chintu.anything.parser;

import java.time.DayOfWeek;
import java.util.List;

/** A "### Mon: Push" section inside a phase, with its exercises. */
public record ParsedDay(DayOfWeek weekday, String title, int line, List<ParsedItem> items) {

    public ParsedDay {
        items = List.copyOf(items);
    }
}
