package com.chintu.anything.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

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
 * Full-stack tests: HTTP → controller → service → JPA → in-memory H2 (with the real Flyway migrations).
 * Each test runs in a transaction that is rolled back, so tests don't see each other's plans.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlanApiIntegrationTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 28); // a Monday

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private PlanRepository plans;

    private static String fixture(String name) throws IOException {
        return Files.readString(Path.of("../fixtures/" + name));
    }

    private String createBody(String planText, LocalDate startDate) throws Exception {
        return json.writeValueAsString(new CreatePlanRequest(planText, startDate));
    }

    /** Saves the calisthenics fixture and returns its new id. */
    private long saveCalisthenics() throws Exception {
        String response = mvc.perform(post("/api/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(fixture("calisthenics.md"), START)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(response);
        return node.get("id").asLong();
    }

    @Test
    void savesAPlanAndReturnsItWithALocation() throws Exception {
        mvc.perform(post("/api/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(fixture("calisthenics.md"), START)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/plans/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("12-Week Calisthenics Strength"))
                .andExpect(jsonPath("$.startDate").value("2026-09-28"))
                .andExpect(jsonPath("$.endDate").value("2026-12-20"))   // 12 weeks later, a Sunday
                .andExpect(jsonPath("$.dayOffset").value(0))
                .andExpect(jsonPath("$.phases.length()").value(3))
                .andExpect(jsonPath("$.phases[0].days.length()").value(4))
                .andExpect(jsonPath("$.phases[0].days[0].weekday").value("MONDAY"))
                .andExpect(jsonPath("$.phases[0].days[0].items[0].name").value("Push-ups"))
                .andExpect(jsonPath("$.phases[0].days[0].items[0].reps.kind").value("COUNT"))
                .andExpect(jsonPath("$.phases[0].days[0].items[0].restSeconds").value(90));
    }

    @Test
    void storesEveryExerciseAndTheOriginalText() throws Exception {
        long id = saveCalisthenics();

        Plan plan = plans.findById(id).orElseThrow();
        long items = plan.getPhases().stream()
                .flatMap(p -> p.getDays().stream())
                .mapToLong(d -> d.getItems().size())
                .sum();
        assertThat(items).isEqualTo(48);
        assertThat(plan.getRawMarkdown()).isEqualTo(fixture("calisthenics.md"));
    }

    @Test
    void getsOnePlanById() throws Exception {
        long id = saveCalisthenics();

        mvc.perform(get("/api/plans/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.phases[2].name").value("Strength"))
                .andExpect(jsonPath("$.phases[2].days[3].items[0].reps.kind").value("MAX"));
    }

    @Test
    void listsSavedPlans() throws Exception {
        long id = saveCalisthenics();

        mvc.perform(get("/api/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].weeks").value(12))
                .andExpect(jsonPath("$[0].phases").doesNotExist());  // the list stays light
    }

    @Test
    void deletesAPlanAndEverythingInIt() throws Exception {
        long id = saveCalisthenics();

        mvc.perform(delete("/api/plans/" + id)).andExpect(status().isNoContent());

        mvc.perform(get("/api/plans/" + id)).andExpect(status().isNotFound());
        assertThat(plans.count()).isZero();
    }

    @Test
    void unknownIdIs404() throws Exception {
        mvc.perform(get("/api/plans/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(containsString("not found")));

        mvc.perform(delete("/api/plans/999999")).andExpect(status().isNotFound());
    }

    @Test
    void brokenPlanIsNotSaved() throws Exception {
        mvc.perform(post("/api/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(fixture("broken/05-unknown-field.md"), START)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.errors[0].line").value(12));

        assertThat(plans.count()).isZero();
    }

    @Test
    void aDayByDayCourseParsesButIsNotSavedYet() throws Exception {
        mvc.perform(post("/api/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(fixture("meditation.md"), START)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("can't be saved yet")));

        assertThat(plans.count()).isZero();
    }

    @Test
    void missingStartDateIsABadRequest() throws Exception {
        mvc.perform(post("/api/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(fixture("calisthenics.md"), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Pick a start date."));
    }
}
