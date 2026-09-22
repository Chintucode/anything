package com.chintu.anything.parser;

/**
 * The reps part of "sets x reps".
 *
 * <ul>
 *   <li>{@code 10} → COUNT, value 10</li>
 *   <li>{@code 10 each leg} → COUNT, value 10, detail "each leg"</li>
 *   <li>{@code 30s} / {@code 2m} → SECONDS, value 30 / 120</li>
 *   <li>{@code max} → MAX (as many as you can)</li>
 *   <li>anything else, e.g. {@code AMRAP 5 min} → TEXT</li>
 * </ul>
 *
 * @param value  the count or seconds; null for MAX and TEXT
 * @param detail extra words after the number, e.g. "each leg"; empty if none
 * @param raw    exactly what the plan said, for display
 */
public record Reps(Kind kind, Integer value, String detail, String raw) {

    public enum Kind { COUNT, SECONDS, MAX, TEXT }
}
