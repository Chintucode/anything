package com.chintu.anything.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** GET /api/plans/{id}/report — the text you paste back into your AI. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportApiIntegrationTest {

    private static final String MON = "2026-09-28";
    private static final String TUE = "2026-09-29";
    private static final String WED = "2026-09-30";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

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

    private List<Long> itemIds(String date) throws Exception {
        String response = mvc.perform(get("/api/plans/" + planId + "/today").param("date", date))
                .andReturn().getResponse().getContentAsString();
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : json.readTree(response).get("items")) {
            ids.add(item.get("id").asLong());
        }
        return ids;
    }

    private void tick(long itemId, String date, Integer actualReps) throws Exception {
        String reps = actualReps == null ? "null" : String.valueOf(actualReps);
        mvc.perform(put("/api/plans/" + planId + "/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\": " + itemId + ", \"date\": \"" + date + "\", \"done\": true,"
                                + " \"actualReps\": " + reps + "}"))
                .andExpect(status().isOk());
    }

    private String report(String date) throws Exception {
        return mvc.perform(get("/api/plans/" + planId + "/report").param("date", date))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void readsLikeSomethingYouCanPasteIntoAChat() throws Exception {
        String text = report(MON);

        assertThat(text)
                .contains("12-Week Calisthenics Strength")
                .contains("12 weeks, starting Mon 28 Sep 2026")
                .contains("week 1 of 12")
                .contains("PROGRESS")
                .contains("WHAT I WANT")
                .contains("Anything format");
    }

    @Test
    void namesTheDaysAndExercisesThatFellShort() throws Exception {
        List<Long> monday = itemIds(MON);
        tick(monday.get(0), MON, 7);          // plan says 4x10 push-ups, managed 7
        tick(monday.get(1), MON, null);
        // Tuesday left untouched entirely.

        String text = report(WED);

        assertThat(text)
                .contains("Push-ups on Mon 28 Sep 2026: plan said 4x10, I managed 7")
                .contains("Push on Mon 28 Sep 2026: only 2 of 4 exercises done")
                .contains("Pull on Tue 29 Sep 2026: only 0 of 4 exercises done");
    }

    @Test
    void mentionsSkippedDays() throws Exception {
        mvc.perform(put("/api/plans/" + planId + "/skips")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\": \"" + MON + "\", \"skipped\": true}"));

        assertThat(report(TUE)).contains("Push on Mon 28 Sep 2026: skipped");
    }

    @Test
    void saysSoWhenNothingHasGoneWrong() throws Exception {
        for (long id : itemIds(MON)) {
            tick(id, MON, null);
        }

        assertThat(report(MON)).contains("Nothing so far");
    }

    @Test
    void unknownPlanIs404() throws Exception {
        mvc.perform(get("/api/plans/999999/report")).andExpect(status().isNotFound());
    }
}
