package com.chintu.anything.plan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The two answers to a missed day, and what they must never cost you.
 *
 * <p>Both of these buttons used to quietly destroy work. Shift moved the plan and left
 * every tick pointing at the wrong day, so the ring, the streak and all twelve week bars
 * read zero. Skip wrote off the whole day including the exercises already done. Neither
 * lost a row in the database — but every number on screen was wrong, which is the same
 * thing to the person looking at it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MissedDayRepairTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private long planId;

    @BeforeEach
    void saveTheCalisthenicsPlan() throws Exception {
        String text = Files.readString(Path.of("../fixtures/calisthenics.md"));
        String body = json.writeValueAsString(new CreatePlanRequest(text, MONDAY));
        String response = mvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        planId = json.readTree(response).get("id").asLong();
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode today(String date) throws Exception {
        return json.readTree(mvc.perform(get("/api/plans/" + planId + "/today").param("date", date))
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode progress(String date) throws Exception {
        return json.readTree(mvc.perform(get("/api/plans/" + planId + "/progress").param("date", date))
                .andReturn().getResponse().getContentAsString());
    }

    private String report(String date) throws Exception {
        return mvc.perform(get("/api/plans/" + planId + "/report").param("date", date))
                .andReturn().getResponse().getContentAsString();
    }

    private List<Long> itemsOn(String date) throws Exception {
        List<Long> ids = new ArrayList<>();
        today(date).get("items").forEach(i -> ids.add(i.get("id").asLong()));
        return ids;
    }

    private void tick(long itemId, String date) throws Exception {
        mvc.perform(put("/api/plans/" + planId + "/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\": " + itemId + ", \"date\": \"" + date + "\", \"done\": true}"))
                .andExpect(status().isOk());
    }

    private void doTheWholeDay(String date) throws Exception {
        for (long id : itemsOn(date)) {
            tick(id, date);
        }
    }

    private void shift(int days) throws Exception {
        mvc.perform(post("/api/plans/" + planId + "/shift")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\": " + days + "}"))
                .andExpect(status().isOk());
    }

    private void skip(String date) throws Exception {
        mvc.perform(put("/api/plans/" + planId + "/skips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\": \"" + date + "\", \"skipped\": true}"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ shift

    @Test
    void shiftingTheDayKeepsEverythingYouHaveDone() throws Exception {
        doTheWholeDay("2026-09-28");
        doTheWholeDay("2026-09-29");
        JsonNode before = progress("2026-09-29");

        shift(1);

        JsonNode after = progress("2026-09-30");   // the same two sessions, one day later
        org.assertj.core.api.Assertions.assertThat(after.get("completed").asInt())
                .isEqualTo(before.get("completed").asInt()).isEqualTo(8);
        org.assertj.core.api.Assertions.assertThat(after.get("percent").asInt()).isEqualTo(100);
        org.assertj.core.api.Assertions.assertThat(after.get("streak").asInt()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(after.get("weeks").get(0).get("completed").asInt()).isEqualTo(8);
    }

    @Test
    void theWorkLandsOnTheDayItNowBelongsTo() throws Exception {
        doTheWholeDay("2026-09-28");
        shift(1);

        // Monday's session is now Tuesday's, still finished.
        org.assertj.core.api.Assertions.assertThat(today("2026-09-29").get("doneCount").asInt()).isEqualTo(4);
        org.assertj.core.api.Assertions.assertThat(today("2026-09-29").get("dayTitle").asText()).isEqualTo("Push");
    }

    @Test
    void aSkippedDayMovesWithThePlanToo() throws Exception {
        skip("2026-09-28");
        shift(1);

        org.assertj.core.api.Assertions.assertThat(today("2026-09-28").get("status").asText())
                .isNotEqualTo("SKIPPED");
        org.assertj.core.api.Assertions.assertThat(today("2026-09-29").get("status").asText())
                .isEqualTo("SKIPPED");
    }

    @Test
    void shiftingBackwardsIsSafeToo() throws Exception {
        doTheWholeDay("2026-09-28");
        shift(7);
        shift(-7);

        JsonNode back = progress("2026-09-29");
        org.assertj.core.api.Assertions.assertThat(back.get("completed").asInt()).isEqualTo(4);
        org.assertj.core.api.Assertions.assertThat(today("2026-09-28").get("doneCount").asInt()).isEqualTo(4);
    }

    @Test
    void shiftingBySevenDoesNotCollideWithTheWeekAfter() throws Exception {
        // Two Mondays' worth of the same items, a week apart: moving by exactly 7 would
        // walk each row onto the next one if they weren't moved in the right order.
        doTheWholeDay("2026-09-28");
        doTheWholeDay("2026-10-05");
        shift(7);

        org.assertj.core.api.Assertions.assertThat(progress("2026-10-12").get("completed").asInt()).isEqualTo(8);
    }

    @Test
    void aCourseCannotBeShiftedAtAll() throws Exception {
        String text = Files.readString(Path.of("../fixtures/meditation.md"));
        String body = json.writeValueAsString(new CreatePlanRequest(text, MONDAY));
        long courseId = json.readTree(mvc.perform(post("/api/plans")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/plans/" + courseId + "/shift")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"days\": 3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", Matchers.containsString("nothing to shift")));
    }

    // ------------------------------------------------------------------ skip

    @Test
    void skippingKeepsTheExercisesYouAlreadyDid() throws Exception {
        List<Long> monday = itemsOn("2026-09-28");
        tick(monday.get(0), "2026-09-28");
        tick(monday.get(1), "2026-09-28");
        tick(monday.get(2), "2026-09-28");

        skip("2026-09-28");

        // Asked on the day itself, so Monday is all that's due: three of four done and
        // then written off reads as three of three, not as nothing.
        JsonNode onTheDay = progress("2026-09-28");
        org.assertj.core.api.Assertions.assertThat(onTheDay.get("completed").asInt()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(onTheDay.get("dueSoFar").asInt()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(onTheDay.get("percent").asInt()).isEqualTo(100);

        // And the week bar keeps them: it used to drop to zero.
        JsonNode later = progress("2026-09-29");
        org.assertj.core.api.Assertions.assertThat(later.get("completed").asInt()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(later.get("weeks").get(0).get("completed").asInt()).isEqualTo(3);
    }

    @Test
    void skippingADayYouDidNothingOnStillCostsNothing() throws Exception {
        skip("2026-09-28");

        JsonNode after = progress("2026-09-29");
        org.assertj.core.api.Assertions.assertThat(after.get("completed").asInt()).isZero();
        org.assertj.core.api.Assertions.assertThat(after.get("dueSoFar").asInt()).isEqualTo(4); // Tuesday only
    }

    @Test
    void theReportSaysHowFarYouGotBeforeSkipping() throws Exception {
        List<Long> monday = itemsOn("2026-09-28");
        tick(monday.get(0), "2026-09-28");
        tick(monday.get(1), "2026-09-28");
        tick(monday.get(2), "2026-09-28");
        skip("2026-09-28");

        org.assertj.core.api.Assertions.assertThat(report("2026-09-30"))
                .contains("skipped after 3 of 4 exercises")
                .doesNotContain(": skipped\n");
    }

    @Test
    void aDayYouSkippedWithNothingDoneStillJustReadsSkipped() throws Exception {
        skip("2026-09-28");
        org.assertj.core.api.Assertions.assertThat(report("2026-09-30")).contains(": skipped");
    }

    @Test
    void theStreakStillWalksStraightPastASkippedDay() throws Exception {
        doTheWholeDay("2026-09-29");
        skip("2026-09-28");
        mvc.perform(get("/api/plans/" + planId + "/progress").param("date", "2026-09-29"))
                .andExpect(content().string(Matchers.containsString("\"streak\":1")));
    }
}
