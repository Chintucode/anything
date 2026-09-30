package com.chintu.anything.health;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets the web app (and you) check that the server is up.
 *
 * <p>Deliberately touches nothing: no database, no queries. That matters because this
 * is also the address a keep-awake pinger hits every few minutes. A health check that
 * woke the database would quietly burn its free allowance around the clock to answer
 * a question that has nothing to do with the database.
 *
 * <p>{@code upFor} is here so you can tell, at a glance, whether the pinger is doing
 * its job. A number that keeps climbing means the server never went to sleep. A number
 * that keeps resetting to a few seconds means it did.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final Instant startedAt = Instant.now();

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("app", "anything");
        body.put("upFor", humanise(Duration.between(startedAt, Instant.now())));
        body.put("upSeconds", Duration.between(startedAt, Instant.now()).toSeconds());
        return body;
    }

    /** "3h 12m", "4m", "18s" — readable at a glance on a phone. */
    static String humanise(Duration up) {
        long hours = up.toHours();
        long minutes = up.toMinutesPart();
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes > 0 ? minutes + "m" : up.toSeconds() + "s";
    }
}
