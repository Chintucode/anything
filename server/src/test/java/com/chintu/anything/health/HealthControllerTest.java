package com.chintu.anything.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

/** Web-layer test only, so it runs without a database. */
@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void returnsOk() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.app").value("anything"));
    }

    /** So you can see whether the keep-awake ping is actually keeping it awake. */
    @Test
    void saysHowLongItHasBeenUp() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upSeconds").isNumber())
                .andExpect(jsonPath("$.upFor").isString());
    }

    @Test
    void theUptimeReadsLikeSomethingAPersonWouldSay() {
        assertThat(HealthController.humanise(Duration.ofSeconds(18))).isEqualTo("18s");
        assertThat(HealthController.humanise(Duration.ofMinutes(4))).isEqualTo("4m");
        assertThat(HealthController.humanise(Duration.ofMinutes(192))).isEqualTo("3h 12m");
        assertThat(HealthController.humanise(Duration.ofHours(30))).isEqualTo("30h 0m");
    }
}
