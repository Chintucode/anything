package com.chintu.anything.plan;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.chintu.anything.parser.ParseResult;
import com.chintu.anything.parser.PlanParser;
import com.chintu.anything.plan.PlanResponses.PlanDetail;
import com.chintu.anything.plan.PlanResponses.PlanSummary;

import jakarta.validation.Valid;

/**
 * Plan endpoints.
 *
 * <ul>
 *   <li>POST   /api/plans/parse   preview a plan (nothing saved): 200, or 422 with line errors</li>
 *   <li>POST   /api/plans         save a plan: 201 with the saved plan, or 422 with line errors</li>
 *   <li>GET    /api/plans         list saved plans, newest first</li>
 *   <li>GET    /api/plans/{id}    one plan with its phases, days and exercises</li>
 *   <li>DELETE /api/plans/{id}    delete a plan: 204</li>
 * </ul>
 * Bad requests (no body, invalid JSON, missing start date) return 400 problem details;
 * unknown ids return 404.
 */
@RestController
@RequestMapping("/api/plans")
public class PlanController {

    public static final int MAX_PLAN_LENGTH = 100_000;

    private final PlanParser parser;
    private final PlanService service;

    public PlanController(PlanParser parser, PlanService service) {
        this.parser = parser;
        this.service = service;
    }

    // ------------------------------------------------------------- preview

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

    // ---------------------------------------------------------------- CRUD

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PlanDetail> create(@Valid @RequestBody CreatePlanRequest request) {
        PlanDetail saved = service.create(request);
        return ResponseEntity.created(URI.create("/api/plans/" + saved.id())).body(saved);
    }

    @GetMapping
    public List<PlanSummary> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public PlanDetail get(@PathVariable long id) {
        return service.get(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    /** Saving a plan with mistakes returns the same 422 shape as the preview. */
    @ExceptionHandler(InvalidPlanException.class)
    public ResponseEntity<PlanPreviewResponse> invalidPlan(InvalidPlanException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(new PlanPreviewResponse(false, null, ex.getErrors()));
    }
}
