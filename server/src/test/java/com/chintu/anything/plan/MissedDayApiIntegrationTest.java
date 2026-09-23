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
 * Skip and Shift: the two answers to a missed day.
 * Calisthenics fixture, Mon/Tue/Thu/Sat, 4 exercises each.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MissedDayApiIntegrationTest {

    private static final String MON = "2026-09-28";
    private static final String TUE = "2026-09-29";
    private static final String WED = "2026-09-30";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private long save(LocalDate startDate) throws Exception {
        String text = Files.readString(Path.of("../fixtures/calisthenics.md"));
        String body = json.writeValueAsString(new CreatePlanRequest(text, startDate));
        String response = mvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private long savedOnMonday() throws Exception {
        return save(LocalDate.of(2026, 9, 28));
    }

    private ResultActions today(long planId, String date) throws Exception {
        return mvc.perform(get("/api/plans/" + planId + "/today").param("date", date));
    }

    private List<Long> itemIds(long planId, String date) throws Exception {
        String response = today(planId, date).andReturn().getResponse().getContentAsString();
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : json.readTree(response).get("items")) {
            ids.add(item.get("id").asLong());
        }
        return ids;
    }

    private void tickAll(long planId, String date) throws Exception {
        for (long id : itemIds(planId, date)) {
            mvc.perform(put("/api/plans/" + planId + "/completions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"itemId\": " + id + ", \"date\": \"" + date + "\", \"done\": true}"))
                    .andExpect(status().isOk());
        }
    }

    private ResultActions skip(long planId, String date, boolean skipped) throws Exception {
        return mvc.perform(put("/api/plans/" + planId + "/skips")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\": \"" + date + "\", \"skipped\": " + skipped + "}"));
    }

    private ResultActions shift(long planId, int days) throws Exception {
        return mvc.perform(post("/api/plans/" + planId + "/shift")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\": " + days + "}"));
    }

    // ------------------------------------------------------- missed days

    @Test
    void anUnfinishedMondayShowsUpOnTuesday() throws Exception {
        long id = savedOnMonday();

        today(id, TUE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missed.date").value(MON))
                .andExpect(jsonPath("$.missed.title").value("Push"))
                .andExpect(jsonPath("$.missed.done").value(0))
                .andExpect(jsonPath("$.missed.total").value(4));
    }

    @Test
    void aFinishedMondayIsNotReported() throws Exception {
        long id = savedOnMonday();
        tickAll(id, MON);

        today(id, TUE).andExpect(jsonPath("$.missed").doesNotExist());
    }

    @Test
    void onlyTheMostRecentTrainingDayIsReported() throws Exception {
        long id = savedOnMonday();
        tickAll(id, TUE);                       // Monday still unfinished, Tuesday done

        today(id, WED).andExpect(jsonPath("$.missed").doesNotExist());
    }

    @Test
    void theFirstDayOfThePlanHasNothingBehindIt() throws Exception {
        long id = savedOnMonday();

        today(id, MON).andExpect(jsonPath("$.missed").doesNotExist());
    }

    // ------------------------------------------------------------- skip

    @Test
    void skippingClearsTheMissedDay() throws Exception {
        long id = savedOnMonday();

        skip(id, MON, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped").value(true));

        today(id, TUE).andExpect(jsonPath("$.missed").doesNotExist());
        today(id, MON).andExpect(jsonPath("$.status").value("SKIPPED"));
    }

    @Test
    void aSkippedDayStopsCountingInProgress() throws Exception {
        long id = savedOnMonday();
        skip(id, MON, true);
        tickAll(id, TUE);

        mvc.perform(get("/api/plans/" + id + "/progress").param("date", WED))
                .andExpect(jsonPath("$.dueSoFar").value(4))     // Monday no longer counts
                .andExpect(jsonPath("$.completed").value(4))
                .andExpect(jsonPath("$.percent").value(100))
                .andExpect(jsonPath("$.streak").value(1));      // and it doesn't break the streak
    }

    @Test
    void unskippingBringsTheDayBack() throws Exception {
        long id = savedOnMonday();
        skip(id, MON, true);
        skip(id, MON, false).andExpect(jsonPath("$.skipped").value(false));

        mvc.perform(get("/api/plans/" + id + "/progress").param("date", WED))
                .andExpect(jsonPath("$.dueSoFar").value(8));
    }

    @Test
    void cannotSkipARestDay() throws Exception {
        long id = savedOnMonday();

        skip(id, WED, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("There's no workout scheduled on 2026-09-30."));
    }

    // ------------------------------------------------------------ shift

    @Test
    void shiftingMovesTodaysWorkout() throws Exception {
        long id = savedOnMonday();

        shift(id, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayOffset").value(1))
                .andExpect(jsonPath("$.endDate").value("2026-12-21"));

        today(id, TUE)                                   // Tuesday now runs Monday's workout
                .andExpect(jsonPath("$.status").value("TRAINING"))
                .andExpect(jsonPath("$.week").value(1))
                .andExpect(jsonPath("$.dayTitle").value("Push"));
    }

    @Test
    void shiftingBackwardsStartsAPlanEarlier() throws Exception {
        long id = save(LocalDate.of(2026, 10, 5));       // starts next Monday

        today(id, WED).andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.daysUntilStart").value(5));

        shift(id, -5).andExpect(status().isOk()).andExpect(jsonPath("$.dayOffset").value(-5));

        today(id, WED)
                .andExpect(jsonPath("$.status").value("TRAINING"))
                .andExpect(jsonPath("$.week").value(1))
                .andExpect(jsonPath("$.dayTitle").value("Push"));
    }

    @Test
    void sillyShiftsAreRejected() throws Exception {
        long id = savedOnMonday();

        shift(id, 400).andExpect(status().isBadRequest());
        shift(id, 0).andExpect(status().isOk()).andExpect(jsonPath("$.dayOffset").value(0));
    }

    @Test
    void unknownPlanIs404() throws Exception {
        skip(999_999L, MON, true).andExpect(status().isNotFound());
        shift(999_999L, 1).andExpect(status().isNotFound());
    }
}
