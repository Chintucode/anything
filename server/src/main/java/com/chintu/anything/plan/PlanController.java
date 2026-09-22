package com.chintu.anything.plan;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.chintu.anything.parser.ParseResult;
import com.chintu.anything.parser.PlanParser;

import jakarta.validation.Valid;

/**
 * Plan endpoints. Day 4: preview only. Nothing is saved until Day 5.
 *
 * <p>Responses:
 * <ul>
 *   <li>200 {@code { "ok": true, "plan": {...}, "errors": [] }} when the plan parses</li>
 *   <li>422 {@code { "ok": false, "errors": [{ "line": 7, "message": "..." }] }} when the
 *       request is fine but the plan has mistakes the user needs to fix</li>
 *   <li>400 problem details when the request itself is bad (no body, too long)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/plans")
public class PlanController {

    public static final int MAX_PLAN_LENGTH = 100_000;

    private final PlanParser parser;

    public PlanController(PlanParser parser) {
        this.parser = parser;
    }

    /** For the web app: JSON body with a "text" field. */
    @PostMapping(path = "/parse", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PlanPreviewResponse> parseJson(@Valid @RequestBody ParseRequest request) {
        return preview(request.text());
    }

    /** For curl and quick testing: the raw plan as the body. */
    @PostMapping(path = "/parse", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<PlanPreviewResponse> parseText(@RequestBody String text) {
        if (text.length() > MAX_PLAN_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The plan is too long (max 100,000 characters).");
        }
        return preview(text);
    }

    private ResponseEntity<PlanPreviewResponse> preview(String text) {
        ParseResult result = parser.parse(text);
        PlanPreviewResponse body = PlanPreviewResponse.from(result);
        return result.isOk()
                ? ResponseEntity.ok(body)
                : ResponseEntity.unprocessableEntity().body(body);
    }
}
