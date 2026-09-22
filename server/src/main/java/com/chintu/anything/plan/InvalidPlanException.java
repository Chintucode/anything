package com.chintu.anything.plan;

import java.util.List;

import com.chintu.anything.parser.ParseError;

/** Thrown when someone tries to save a plan that doesn't parse. Mapped to a 422. */
public class InvalidPlanException extends RuntimeException {

    private final transient List<ParseError> errors;

    public InvalidPlanException(List<ParseError> errors) {
        super("Plan has " + errors.size() + " error(s)");
        this.errors = List.copyOf(errors);
    }

    public List<ParseError> getErrors() {
        return errors;
    }
}
