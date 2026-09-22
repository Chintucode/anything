package com.chintu.anything.plan;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.chintu.anything.config.ParserConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Web-layer tests for POST /api/plans/parse. No database needed (the service is mocked). */
@WebMvcTest(PlanController.class)
@Import(ParserConfig.class)
class PlanControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private PlanService planService;

    private static String fixture(String name) throws IOException {
        return Files.readString(Path.of("../fixtures/" + name));
    }

    @Test
    void plainTextPlanReturnsThePreview() throws Exception {
        mvc.perform(post("/api/plans/parse")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(fixture("calisthenics.md")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.plan.header.title").value("12-Week Calisthenics Strength"))
                .andExpect(jsonPath("$.plan.header.weeks").value(12))
                .andExpect(jsonPath("$.plan.phases.length()").value(3))
                .andExpect(jsonPath("$.plan.phases[0].days[0].weekday").value("MONDAY"))
                .andExpect(jsonPath("$.plan.phases[0].days[0].items[0].name").value("Push-ups"))
                .andExpect(jsonPath("$.plan.phases[0].days[0].items[0].reps.kind").value("COUNT"))
                .andExpect(jsonPath("$.errors.length()").value(0));
    }

    @Test
    void jsonPlanReturnsThePreview() throws Exception {
        String body = json.writeValueAsString(new ParseRequest(fixture("calisthenics.md")));

        mvc.perform(post("/api/plans/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.plan.header.weeks").value(12));
    }

    @Test
    void brokenPlanReturns422WithLineErrors() throws Exception {
        mvc.perform(post("/api/plans/parse")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(fixture("broken/04-bad-sets-reps.md")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.plan").doesNotExist())
                .andExpect(jsonPath("$.errors[0].line").value(12))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("isn't sets x reps")));
    }

    @Test
    void blankJsonTextIsABadRequest() throws Exception {
        mvc.perform(post("/api/plans/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("Paste a plan first."));
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mvc.perform(post("/api/plans/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void tooLongPlainTextIsABadRequest() throws Exception {
        mvc.perform(post("/api/plans/parse")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("a".repeat(PlanController.MAX_PLAN_LENGTH + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("too long")));
    }

    @Test
    void unsupportedContentTypeIsRejected() throws Exception {
        mvc.perform(post("/api/plans/parse")
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<plan/>"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
