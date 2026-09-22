package com.chintu.anything.plan;

import java.util.List;

import com.chintu.anything.parser.ParseError;
import com.chintu.anything.parser.ParseResult;
import com.chintu.anything.parser.ParsedPlan;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * JSON returned by POST /api/plans/parse.
 * Kept separate from the parser's own types so the API shape is explicit and stable.
 *
 * @param plan null (and left out of the JSON) when the plan has errors
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanPreviewResponse(boolean ok, ParsedPlan plan, List<ParseError> errors) {

    static PlanPreviewResponse from(ParseResult result) {
        return new PlanPreviewResponse(result.isOk(), result.plan(), result.errors());
    }
}
