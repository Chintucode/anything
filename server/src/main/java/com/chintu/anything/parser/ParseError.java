package com.chintu.anything.parser;

/**
 * One problem found while parsing a plan.
 *
 * @param line    1-based line number in the pasted plan
 * @param message plain-English explanation the user can act on
 */
public record ParseError(int line, String message) {

    @Override
    public String toString() {
        return "Line " + line + ": " + message;
    }
}
