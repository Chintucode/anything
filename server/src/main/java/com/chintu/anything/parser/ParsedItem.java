package com.chintu.anything.parser;

/**
 * One exercise line: "- Push-ups | 3x10 | rest 60s | note: elbows in".
 *
 * @param restSeconds null when the line has no rest field
 * @param note        empty when the line has no note
 */
public record ParsedItem(String name, int sets, Reps reps, Integer restSeconds, String note, int line) {
}
