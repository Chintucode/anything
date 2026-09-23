package com.chintu.anything.plan;

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

/** GET /api/plans/{id}/week — the strip at the top of Today. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WeekApiIntegrationTest {

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

    private ResultActions week(String date) throws Exception {
        return mvc.perform(get("/api/plans/" + planId + "/week").param("date", date));
    }

    @Test
    void returnsMondayToSundayWithEachDaysState() throws Exception {
        week("2026-09-30")                                   // a Wednesday
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-09-28"))
                .andExpect(jsonPath("$.to").value("2026-10-04"))
                .andExpect(jsonPath("$.days.length()").value(7))
                .andExpect(jsonPath("$.days[0].weekday").value("MONDAY"))
                .andExpect(jsonPath("$.days[0].status").value("TRAINING"))
                .andExpect(jsonPath("$.days[0].title").value("Push"))
                .andExpect(jsonPath("$.days[0].total").value(4))
                .andExpect(jsonPath("$.days[0].planWeek").value(1))
                .andExpect(jsonPath("$.days[2].status").value("REST"))    // Wednesday
                .andExpect(jsonPath("$.days[3].title").value("Legs + Core"));
    }

    @Test
    void countsWhatYouTicked() throws Exception {
        String monday = "2026-09-28";
        String response = mvc.perform(get("/api/plans/" + planId + "/today").param("date", monday))
                .andReturn().getResponse().getContentAsString();
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : json.readTree(response).get("items")) {
            ids.add(item.get("id").asLong());
        }
        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/api/plans/" + planId + "/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"itemId\": " + ids.get(i) + ", \"date\": \"" + monday + "\", \"done\": true}"));
        }

        week(monday)
                .andExpect(jsonPath("$.days[0].done").value(2))
                .andExpect(jsonPath("$.days[0].total").value(4));
    }

    @Test
    void showsSkippedDays() throws Exception {
        mvc.perform(put("/api/plans/" + planId + "/skips")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\": \"2026-09-28\", \"skipped\": true}"));

        week("2026-09-28").andExpect(jsonPath("$.days[0].status").value("SKIPPED"));
    }

    @Test
    void daysOutsideThePlanSaySo() throws Exception {
        week("2026-09-21").andExpect(jsonPath("$.days[0].status").value("NOT_STARTED"));
        week("2026-12-28").andExpect(jsonPath("$.days[0].status").value("FINISHED"));
    }

    @Test
    void unknownPlanIs404() throws Exception {
        mvc.perform(get("/api/plans/999999/week")).andExpect(status().isNotFound());
    }
}
