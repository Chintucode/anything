package com.chintu.anything.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Ticking exercises and reading progress, against the saved calisthenics plan.
 * Start: Monday 2026-09-28. Training days: Mon, Tue, Thu, Sat, 4 exercises each (16 per week).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TrackingApiIntegrationTest {

    private static final String MON = "2026-09-28";
    private static final String TUE = "2026-09-29";
    private static final String WED = "2026-09-30";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private CompletionRepository completions;

    private long planId;

    @BeforeEach
    void savePlan() throws Exception {
        String text = Files.readString(Path.of("../fixtures/calisthenics.md"));
        String body = json.writeValueAsString(new CreatePlanRequest(text, LocalDate.of(2026, 9, 28)));
        String response = mvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        planId = json.readTree(response).get("id").asLong();
    }

    /** Item ids on the Today list for a date, in order. */
    private List<Long> itemIds(String date) throws Exception {
        String response = mvc.perform(get("/api/plans/" + planId + "/today").param("date", date))
                .andReturn().getResponse().getContentAsString();
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : json.readTree(response).get("items")) {
            ids.add(item.get("id").asLong());
        }
        return ids;
    }

    private ResultActions rest(String date, boolean rested) throws Exception {
        String body = "{\"date\": \"" + date + "\", \"rested\": " + rested + "}";
        return mvc.perform(put("/api/plans/" + planId + "/rests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions tick(long itemId, String date, boolean done) throws Exception {
        String body = "{\"itemId\": " + itemId + ", \"date\": \"" + date + "\", \"done\": " + done + "}";
        return mvc.perform(put("/api/plans/" + planId + "/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void tickAll(List<Long> ids, String date) throws Exception {
        for (long id : ids) {
            tick(id, date, true).andExpect(status().isOk());
        }
    }

    private ResultActions progress(String date) throws Exception {
        return mvc.perform(get("/api/plans/" + planId + "/progress").param("date", date));
    }

    // -------------------------------------------------------- completions

    @Test
    void tickingShowsUpOnTheTodayList() throws Exception {
        long pushUps = itemIds(MON).get(0);

        tick(pushUps, MON, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value(pushUps))
                .andExpect(jsonPath("$.done").value(true));

        mvc.perform(get("/api/plans/" + planId + "/today").param("date", MON))
                .andExpect(jsonPath("$.items[0].done").value(true))
                .andExpect(jsonPath("$.items[1].done").value(false))
                .andExpect(jsonPath("$.doneCount").value(1));
    }

    @Test
    void untickingClearsIt() throws Exception {
        long pushUps = itemIds(MON).get(0);
        tick(pushUps, MON, true);

        tick(pushUps, MON, false).andExpect(status().isOk()).andExpect(jsonPath("$.done").value(false));

        mvc.perform(get("/api/plans/" + planId + "/today").param("date", MON))
                .andExpect(jsonPath("$.items[0].done").value(false))
                .andExpect(jsonPath("$.doneCount").value(0));
    }

    @Test
    void tickingTwiceIsHarmless() throws Exception {
        long pushUps = itemIds(MON).get(0);

        tick(pushUps, MON, true).andExpect(status().isOk());
        tick(pushUps, MON, true).andExpect(status().isOk());

        assertThat(completions.count()).isEqualTo(1);
    }

    @Test
    void untickingSomethingNeverTickedIsFine() throws Exception {
        tick(itemIds(MON).get(0), MON, false).andExpect(status().isOk());
        assertThat(completions.count()).isZero();
    }

    @Test
    void loggingWhatYouActuallyDid() throws Exception {
        long pushUps = itemIds(MON).get(0);

        // Ticked with a lower number than the plan asked for.
        mvc.perform(put("/api/plans/" + planId + "/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\": " + pushUps + ", \"date\": \"" + MON + "\", \"done\": true, \"actualReps\": 7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualReps").value(7));

        mvc.perform(get("/api/plans/" + planId + "/today").param("date", MON))
                .andExpect(jsonPath("$.items[0].done").value(true))
                .andExpect(jsonPath("$.items[0].actualReps").value(7));

        // Sending it again without the number clears it: "actually, as written".
        tick(pushUps, MON, true).andExpect(status().isOk());
        mvc.perform(get("/api/plans/" + planId + "/today").param("date", MON))
                .andExpect(jsonPath("$.items[0].actualReps").doesNotExist());
    }

    @Test
    void sillyRepCountsAreRejected() throws Exception {
        long pushUps = itemIds(MON).get(0);

        mvc.perform(put("/api/plans/" + planId + "/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\": " + pushUps + ", \"date\": \"" + MON + "\", \"done\": true, \"actualReps\": -3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotTickAnExerciseOnADayItIsNotScheduled() throws Exception {
        long mondayPushUps = itemIds(MON).get(0);

        tick(mondayPushUps, WED, true)          // Wednesday is a rest day
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("That exercise isn't scheduled on 2026-09-30."));
        tick(mondayPushUps, TUE, true)          // Tuesday has different exercises
                .andExpect(status().isBadRequest());
        tick(999_999L, MON, true)               // not an exercise in this plan
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingFieldsAreABadRequest() throws Exception {
        mvc.perform(put("/api/plans/" + planId + "/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\": \"2026-09-28\", \"done\": true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("itemId is required."));
    }

    @Test
    void unknownPlanIs404() throws Exception {
        mvc.perform(put("/api/plans/999999/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\": 1, \"date\": \"2026-09-28\", \"done\": true}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/plans/999999/progress")).andExpect(status().isNotFound());
    }

    // ----------------------------------------------------------- progress

    @Test
    void beforeTheStartEverythingIsZero() throws Exception {
        progress("2026-09-25")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percent").value(0))
                .andExpect(jsonPath("$.dueSoFar").value(0))
                .andExpect(jsonPath("$.streak").value(0))
                .andExpect(jsonPath("$.totalItems").value(192))     // 12 weeks x 16
                .andExpect(jsonPath("$.weeks.length()").value(12))
                .andExpect(jsonPath("$.weeks[0].scheduled").value(16));
    }

    @Test
    void todayCountsAsSoonAsItIsDue() throws Exception {
        // Monday fully done; Tuesday (today) half done.
        tickAll(itemIds(MON), MON);
        List<Long> tuesday = itemIds(TUE);
        tick(tuesday.get(0), TUE, true);
        tick(tuesday.get(1), TUE, true);

        progress(TUE)
                .andExpect(jsonPath("$.completed").value(6))
                .andExpect(jsonPath("$.dueSoFar").value(8))     // 4 on Monday, 4 more today
                .andExpect(jsonPath("$.percent").value(75))     // and the percentage says so
                .andExpect(jsonPath("$.streak").value(1));      // the streak stays forgiving, though
    }

    @Test
    void theCountAndThePercentageAgree() throws Exception {
        // Nothing ticked at all on the first morning: 0 of 4, not 0 of 0.
        progress(MON)
                .andExpect(jsonPath("$.completed").value(0))
                .andExpect(jsonPath("$.dueSoFar").value(4))
                .andExpect(jsonPath("$.percent").value(0));

        List<Long> monday = itemIds(MON);
        tick(monday.get(0), MON, true);

        progress(MON)
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.dueSoFar").value(4))     // denominator stays put as you tick
                .andExpect(jsonPath("$.percent").value(25));    // the ring is the fraction, drawn
    }

    @Test
    void aHalfDoneDayCountsOnceItIsOver() throws Exception {
        tickAll(itemIds(MON), MON);
        List<Long> tuesday = itemIds(TUE);
        tick(tuesday.get(0), TUE, true);
        tick(tuesday.get(1), TUE, true);

        progress(WED)
                .andExpect(jsonPath("$.completed").value(6))
                .andExpect(jsonPath("$.dueSoFar").value(8))     // Wednesday is a rest day: nothing due
                .andExpect(jsonPath("$.percent").value(75))
                .andExpect(jsonPath("$.streak").value(0))       // Tuesday broke it
                .andExpect(jsonPath("$.weeks[0].completed").value(6));
    }

    @Test
    void restDaysDoNotBreakTheStreak() throws Exception {
        tickAll(itemIds(MON), MON);
        tickAll(itemIds(TUE), TUE);

        progress(WED)                                           // Wednesday is a rest day
                .andExpect(jsonPath("$.percent").value(100))
                .andExpect(jsonPath("$.streak").value(2));
    }

    @Test
    void aMissedDayBreaksTheStreakAndLowersThePercentage() throws Exception {
        // Monday skipped entirely, Tuesday fully done.
        tickAll(itemIds(TUE), TUE);

        progress(WED)
                .andExpect(jsonPath("$.completed").value(4))
                .andExpect(jsonPath("$.dueSoFar").value(8))
                .andExpect(jsonPath("$.percent").value(50))
                .andExpect(jsonPath("$.streak").value(1));
    }

    // ----------------------------------------------------------- rest days

    @Test
    void aRestDayCanBeMarkedAsTakenAndUnmarked() throws Exception {
        mvc.perform(get("/api/plans/" + planId + "/today").param("date", WED))
                .andExpect(jsonPath("$.status").value("REST"))
                .andExpect(jsonPath("$.rested").value(false));

        rest(WED, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.rested").value(true));

        mvc.perform(get("/api/plans/" + planId + "/today").param("date", WED))
                .andExpect(jsonPath("$.rested").value(true));

        rest(WED, true).andExpect(status().isOk());      // idempotent: no second row

        rest(WED, false).andExpect(status().isOk());
        mvc.perform(get("/api/plans/" + planId + "/today").param("date", WED))
                .andExpect(jsonPath("$.rested").value(false));
    }

    @Test
    void aTrainingDayCannotBeRested() throws Exception {
        rest(MON, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("2026-09-28 is a training day, not a rest day."));
    }

    @Test
    void aDayOutsideThePlanCannotBeRested() throws Exception {
        rest("2026-09-20", true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("2026-09-20 isn't part of this plan."));
    }

    @Test
    void restingChangesNoNumbers() throws Exception {
        tickAll(itemIds(MON), MON);
        tickAll(itemIds(TUE), TUE);
        rest(WED, true);

        progress(WED)                                   // exactly as it reads without the rest tick
                .andExpect(jsonPath("$.completed").value(8))
                .andExpect(jsonPath("$.dueSoFar").value(8))
                .andExpect(jsonPath("$.percent").value(100))
                .andExpect(jsonPath("$.streak").value(2));
    }

    @Test
    void aTrainingDayNeverReportsRested() throws Exception {
        mvc.perform(get("/api/plans/" + planId + "/today").param("date", MON))
                .andExpect(jsonPath("$.status").value("TRAINING"))
                .andExpect(jsonPath("$.rested").doesNotExist());
    }
}
