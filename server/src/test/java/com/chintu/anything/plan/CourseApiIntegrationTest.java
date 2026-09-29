package com.chintu.anything.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
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
 * A day-by-day course, end to end: the 21-day meditation fixture, started Monday 2026-09-28.
 *
 * <p>The rules under test, in the words the three AI models used when asked for this
 * plan: "Miss a day? Just resume the next day. Don't restart, don't double up."
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CourseApiIntegrationTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 28);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private long planId;
    private JsonNode saved;

    @BeforeEach
    void saveCourse() throws Exception {
        String text = Files.readString(Path.of("../fixtures/meditation.md"));
        String body = json.writeValueAsString(new CreatePlanRequest(text, START));
        String response = mvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        saved = json.readTree(response);
        planId = saved.get("id").asLong();
    }

    // ------------------------------------------------------------ helpers

    private static String day(int n) {
        return START.plusDays(n - 1L).toString();
    }

    private ResultActions today(String date) throws Exception {
        return mvc.perform(get("/api/plans/" + planId + "/today").param("date", date));
    }

    private ResultActions progress(String date) throws Exception {
        return mvc.perform(get("/api/plans/" + planId + "/progress").param("date", date));
    }

    private ResultActions tick(long itemId, String date, boolean done) throws Exception {
        return mvc.perform(put("/api/plans/" + planId + "/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\": " + itemId + ", \"date\": \"" + date + "\", \"done\": " + done + "}"));
    }

    /** Item ids of whatever day Today shows on a date. */
    private List<Long> itemsOnScreen(String date) throws Exception {
        JsonNode node = json.readTree(today(date).andReturn().getResponse().getContentAsString());
        List<Long> ids = new ArrayList<>();
        node.get("items").forEach(i -> ids.add(i.get("id").asLong()));
        return ids;
    }

    /** Does the day on screen, all of it, on that date. */
    private void finishTodaysDay(String date) throws Exception {
        for (long id : itemsOnScreen(date)) {
            tick(id, date, true).andExpect(status().isOk());
        }
    }

    // ------------------------------------------------------------ saving

    @Test
    void aCourseSavesWithItsShape() {
        assertThat(saved.get("schedule").asText()).isEqualTo("SEQUENTIAL");
        assertThat(saved.get("totalDays").asInt()).isEqualTo(21);
        assertThat(saved.get("weeks").asInt()).isEqualTo(3);
        assertThat(saved.get("endDate").asText()).isEqualTo("2026-10-18");   // the earliest it can finish
        JsonNode day1 = saved.get("phases").get(0).get("days").get(0);
        assertThat(day1.get("dayNumber").asInt()).isEqualTo(1);
        assertThat(day1.get("weekday").isNull()).isTrue();
        assertThat(day1.get("description").asText()).startsWith("Sit comfortably");
    }

    // ------------------------------------------------------------ today

    @Test
    void beforeTheStartItPointsAtDayOne() throws Exception {
        today(START.minusDays(1).toString())
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.daysUntilStart").value(1))
                .andExpect(jsonPath("$.next.title").value("Just Breathe"))
                .andExpect(jsonPath("$.next.date").value(day(1)));
    }

    @Test
    void theFirstDayIsDayOneWithItsInstructions() throws Exception {
        today(day(1))
                .andExpect(jsonPath("$.status").value("TRAINING"))
                .andExpect(jsonPath("$.schedule").value("SEQUENTIAL"))
                .andExpect(jsonPath("$.dayNumber").value(1))
                .andExpect(jsonPath("$.totalDays").value(21))
                .andExpect(jsonPath("$.week").value(1))
                .andExpect(jsonPath("$.totalWeeks").value(3))
                .andExpect(jsonPath("$.phaseName").value("Showing Up"))
                .andExpect(jsonPath("$.dayTitle").value("Just Breathe"))
                .andExpect(jsonPath("$.description", startsWith("Sit comfortably")))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.missed").doesNotExist())
                .andExpect(jsonPath("$.rested").doesNotExist());
    }

    @Test
    void finishingADayKeepsItOnScreenAndOffersTheNextOneTomorrow() throws Exception {
        finishTodaysDay(day(1));

        today(day(1))
                .andExpect(jsonPath("$.dayNumber").value(1))          // no doubling up
                .andExpect(jsonPath("$.doneCount").value(1))
                .andExpect(jsonPath("$.next.title").value("Body Scan"))
                .andExpect(jsonPath("$.next.date").value(day(2)));
    }

    @Test
    void theNextCalendarDayMovesOn() throws Exception {
        finishTodaysDay(day(1));

        today(day(2)).andExpect(jsonPath("$.dayNumber").value(2));
    }

    @Test
    void aMissedDayWaitsInsteadOfBeingLost() throws Exception {
        // Nothing done at all, and two days have gone by.
        today(day(3))
                .andExpect(jsonPath("$.status").value("TRAINING"))
                .andExpect(jsonPath("$.dayNumber").value(1))
                .andExpect(jsonPath("$.missed").doesNotExist());

        finishTodaysDay(day(3));
        today(day(4)).andExpect(jsonPath("$.dayNumber").value(2));
    }

    @Test
    void aHalfDoneDayCarriesOverAndCanBeUntickedLater() throws Exception {
        for (int n = 1; n <= 4; n++) {
            finishTodaysDay(day(n));
        }
        List<Long> day5 = itemsOnScreen(day(5));
        assertThat(day5).hasSize(2);                                  // "Breath and Body"
        tick(day5.get(0), day(5), true).andExpect(status().isOk());

        today(day(6))
                .andExpect(jsonPath("$.dayNumber").value(5))          // still waiting
                .andExpect(jsonPath("$.doneCount").value(1));

        // Ticked yesterday, unticked today: the tick is found whatever its date.
        tick(day5.get(0), day(6), false).andExpect(status().isOk());
        today(day(6)).andExpect(jsonPath("$.doneCount").value(0));
    }

    @Test
    void finishingTheLastDayFinishesTheCourse() throws Exception {
        for (int n = 1; n <= 21; n++) {
            finishTodaysDay(day(n));
        }
        today(day(21))
                .andExpect(jsonPath("$.dayNumber").value(21))
                .andExpect(jsonPath("$.next").doesNotExist());         // nothing left to offer
        today(day(22)).andExpect(jsonPath("$.status").value("FINISHED"));
    }

    // ------------------------------------------------------------ progress

    @Test
    void progressIsTheWholeCourseInSevenDayBars() throws Exception {
        finishTodaysDay(day(1));

        progress(day(1))
                .andExpect(jsonPath("$.totalItems").value(25))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.weeks.length()").value(3))
                .andExpect(jsonPath("$.weeks[0].completed").value(1));
    }

    @Test
    void theStreakCountsCalendarDaysAndTodayIsNotOverYet() throws Exception {
        finishTodaysDay(day(1));
        finishTodaysDay(day(2));
        finishTodaysDay(day(3));

        progress(day(3)).andExpect(jsonPath("$.streak").value(3));
        progress(day(4)).andExpect(jsonPath("$.streak").value(3));   // day 4 isn't over
        progress(day(5)).andExpect(jsonPath("$.streak").value(0));   // day 4 went by
    }

    // ------------------------------------------------------------ what a course doesn't do

    @Test
    void aCourseCannotSkipOrMarkRest() throws Exception {
        mvc.perform(put("/api/plans/" + planId + "/skips").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\": \"" + day(1) + "\", \"skipped\": true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("simply waits for you")));
        mvc.perform(put("/api/plans/" + planId + "/rests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\": \"" + day(1) + "\", \"rested\": true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aCourseHasNoWeekdayStrip() throws Exception {
        mvc.perform(get("/api/plans/" + planId + "/week").param("date", day(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(0));
    }

    @Test
    void anItemFromAnotherPlanIsRefused() throws Exception {
        tick(999_999, day(1), true).andExpect(status().isBadRequest());
    }

    @Test
    void theReportSpeaksInDays() throws Exception {
        finishTodaysDay(day(1));

        String report = mvc.perform(get("/api/plans/" + planId + "/report").param("date", day(2)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(report)
                .contains("21 days, worked through in order")
                .contains("on day 2 of 21")
                .contains("1 of 21 days finished")
                .contains("### Day N: Title")
                .doesNotContain("### Mon");
    }
}
