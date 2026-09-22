package com.chintu.anything.parser;

import java.util.List;

/**
 * Either a parsed plan or a list of errors, never both.
 * The parser collects every error it can find so the user fixes them in one go.
 */
public record ParseResult(ParsedPlan plan, List<ParseError> errors) {

    public ParseResult {
        errors = List.copyOf(errors);
    }

    public static ParseResult success(ParsedPlan plan) {
        return new ParseResult(plan, List.of());
    }

    public static ParseResult failure(List<ParseError> errors) {
        return new ParseResult(null, errors);
    }

    public boolean isOk() {
        return errors.isEmpty();
    }
}
