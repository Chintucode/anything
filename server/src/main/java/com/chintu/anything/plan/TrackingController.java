package com.chintu.anything.plan;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chintu.anything.plan.CompletionService.CompletionResponse;
import com.chintu.anything.plan.DayAdjustmentService.RestResponse;
import com.chintu.anything.plan.DayAdjustmentService.SkipResponse;
import com.chintu.anything.plan.PlanResponses.PlanDetail;

import jakarta.validation.Valid;

/**
 * PUT  /api/plans/{id}/completions             tick or untick one exercise on one date
 * GET  /api/plans/{id}/progress[?date=...]     percent, streak and weekly bars
 * PUT  /api/plans/{id}/skips                   write off (or restore) one training day
 * PUT  /api/plans/{id}/rests                   mark a rest day as taken (or undo that)
 * POST /api/plans/{id}/shift                   move the whole plan later or earlier
 * GET  /api/plans/{id}/report[?date=...]       plain-text progress report to paste back into an AI
 */
@RestController
public class TrackingController {

    private final CompletionService completionService;
    private final ProgressService progressService;
    private final DayAdjustmentService adjustments;
    private final ReportService reportService;

    public TrackingController(CompletionService completionService, ProgressService progressService,
            DayAdjustmentService adjustments, ReportService reportService) {
        this.completionService = completionService;
        this.progressService = progressService;
        this.adjustments = adjustments;
        this.reportService = reportService;
    }

    @PutMapping("/api/plans/{id}/completions")
    public CompletionResponse setCompletion(@PathVariable long id, @Valid @RequestBody CompletionRequest request) {
        return completionService.set(id, request);
    }

    /** "I missed it, move on." */
    @PutMapping("/api/plans/{id}/skips")
    public SkipResponse setSkipped(@PathVariable long id, @Valid @RequestBody SkipRequest request) {
        return adjustments.setSkipped(id, request.date(), request.skipped());
    }

    /** "I rested, that's done." Only on days the plan left empty. */
    @PutMapping("/api/plans/{id}/rests")
    public RestResponse setRested(@PathVariable long id, @Valid @RequestBody RestRequest request) {
        return adjustments.setRested(id, request.date(), request.rested());
    }

    /** "Push everything back a day." Negative days pull the plan earlier. */
    @PostMapping("/api/plans/{id}/shift")
    public PlanDetail shift(@PathVariable long id, @Valid @RequestBody ShiftRequest request) {
        return adjustments.shift(id, request.days());
    }

    /** Plain text on purpose: it's made to be copied straight into a chat with an AI. */
    @GetMapping(path = "/api/plans/{id}/report", produces = MediaType.TEXT_PLAIN_VALUE)
    public String report(
            @PathVariable long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reportService.report(id, date != null ? date : LocalDate.now());
    }

    @GetMapping("/api/plans/{id}/progress")
    public ProgressResponse progress(
            @PathVariable long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return progressService.progress(id, date != null ? date : LocalDate.now());
    }
}
