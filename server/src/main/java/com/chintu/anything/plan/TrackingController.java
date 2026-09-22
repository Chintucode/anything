package com.chintu.anything.plan;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chintu.anything.plan.CompletionService.CompletionResponse;

import jakarta.validation.Valid;

/**
 * PUT /api/plans/{id}/completions              tick or untick one exercise on one date
 * GET /api/plans/{id}/progress[?date=...]      percent, streak and weekly bars
 */
@RestController
public class TrackingController {

    private final CompletionService completionService;
    private final ProgressService progressService;

    public TrackingController(CompletionService completionService, ProgressService progressService) {
        this.completionService = completionService;
        this.progressService = progressService;
    }

    @PutMapping("/api/plans/{id}/completions")
    public CompletionResponse setCompletion(@PathVariable long id, @Valid @RequestBody CompletionRequest request) {
        return completionService.set(id, request);
    }

    @GetMapping("/api/plans/{id}/progress")
    public ProgressResponse progress(
            @PathVariable long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return progressService.progress(id, date != null ? date : LocalDate.now());
    }
}
