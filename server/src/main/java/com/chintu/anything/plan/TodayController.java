package com.chintu.anything.plan;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/plans/{id}/today            what to do today (server's date)
 * GET /api/plans/{id}/today?date=...   what to do on any date, e.g. 2026-10-05
 *
 * <p>The web app will always send its own local date, so the answer follows the
 * user's time zone, not the server's.
 */
@RestController
public class TodayController {

    private final TodayService service;

    public TodayController(TodayService service) {
        this.service = service;
    }

    @GetMapping("/api/plans/{id}/today")
    public TodayResponse today(
            @PathVariable long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.today(id, date != null ? date : LocalDate.now());
    }
}
