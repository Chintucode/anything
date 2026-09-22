package com.chintu.anything.plan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

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

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * GET /api/plans/{id}/today against the saved calisthenics plan.
 * Start date is Monday 2026-09-28; training days are Mon, Tue, Thu and Sat.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TodayApiIntegrationTest {

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

    private ResultActions today(String date) throws Exception {
        return mvc.perform(get("/api/plans/" + planId + "/today").param("date", date));
    }

    @Test
    void firstMondayIsWeekOnePush() throws Exception {
        today("2026-09-28")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRAINING"))
                .andExpect(jsonPath("$.week").value(1))
                .andExpect(jsonPath("$.totalWeeks").value(12))
                .andExpect(jsonPath("$.phaseName").value("Foundation"))
                .andExpect(jsonPath("$.dayTitle").value("Push"))
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[0].name").value("Push-ups"))
                .andExpect(jsonPath("$.next").doesNotExist());
    }

    @Test
    void wednesdayIsARestDayPointingAtThursday() throws Exception {
        today("2026-09-30")
                .andExpect(jsonPath("$.status").value("REST"))
                .andExpect(jsonPath("$.week").value(1))
                .andExpect(jsonPath("$.items").doesNotExist())
                .andExpect(jsonPath("$.next.date").value("2026-10-01"))
                .andExpect(jsonPath("$.next.title").value("Legs + Core"));
    }

    @Test
    void weekFiveSwitchesToTheBuildPhase() throws Exception {
        today("2026-10-26")
                .andExpect(jsonPath("$.status").value("TRAINING"))
                .andExpect(jsonPath("$.week").value(5))
                .andExpect(jsonPath("$.phaseName").value("Build"))
                .andExpect(jsonPath("$.items[0].name").value("Diamond push-ups"));
    }

    @Test
    void lastSaturdayIsTestDay() throws Exception {
        today("2026-12-19")
                .andExpect(jsonPath("$.status").value("TRAINING"))
                .andExpect(jsonPath("$.week").value(12))
                .andExpect(jsonPath("$.dayTitle").value("Test Day"))
                .andExpect(jsonPath("$.items[0].reps.kind").value("MAX"));
    }

    @Test
    void lastSundayIsRestWithNoNextWorkout() throws Exception {
        today("2026-12-20")
                .andExpect(jsonPath("$.status").value("REST"))
                .andExpect(jsonPath("$.next").doesNotExist());
    }

    @Test
    void afterTheLastDayIsFinished() throws Exception {
        today("2026-12-21")
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.week").doesNotExist());
    }

    @Test
    void beforeTheStartShowsTheCountdownAndFirstWorkout() throws Exception {
        today("2026-09-25")
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.daysUntilStart").value(3))
                .andExpect(jsonPath("$.next.date").value("2026-09-28"))
                .andExpect(jsonPath("$.next.title").value("Push"));
    }

    @Test
    void worksWithoutADate() throws Exception {
        mvc.perform(get("/api/plans/" + planId + "/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void badDateIsABadRequest() throws Exception {
        today("next-monday").andExpect(status().isBadRequest());
    }

    @Test
    void unknownPlanIs404() throws Exception {
        mvc.perform(get("/api/plans/999999/today")).andExpect(status().isNotFound());
    }
}
