package com.chintu.anything.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import com.chintu.anything.health.HealthController;

/**
 * One URL for the app and the API. The web build is stood in for by the small files
 * in src/test/resources/static.
 */
@WebMvcTest(HealthController.class)
class WebAppConfigTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void theRootIsTheApp() throws Exception {
        // Spring Boot's welcome page: a forward, which a real server follows.
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @Test
    void reloadingAScreenStillGetsTheApp() throws Exception {
        for (String screen : new String[] {"/plans", "/plans/new"}) {
            mvc.perform(get(screen))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<div id=\"root\">")))
                    .andExpect(header().string("Cache-Control", "no-cache"));
        }
    }

    @Test
    void hashedAssetsAreKeptForAYear() throws Exception {
        String cache = mvc.perform(get("/assets/index-1a2b3c.js"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Cache-Control");
        assertThat(cache).contains("max-age=31536000").contains("immutable");
    }

    @Test
    void theServiceWorkerIsCheckedEveryTime() throws Exception {
        mvc.perform(get("/sw.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"));
        mvc.perform(get("/manifest.webmanifest")).andExpect(status().isOk());
    }

    @Test
    void theApiStillAnswers() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void aWrongApiAddressIsA404NotTheApp() throws Exception {
        mvc.perform(get("/api/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<div id=\"root\">"))));
    }

    @Test
    void aMissingFileIsA404() throws Exception {
        mvc.perform(get("/assets/gone-123.js")).andExpect(status().isNotFound());
        mvc.perform(get("/old-icon.png")).andExpect(status().isNotFound());
    }

    @Test
    void screenPathsAreToldApartFromFiles() {
        assertThat(WebAppConfig.AppScreenResolver.isScreen("plans/new")).isTrue();
        assertThat(WebAppConfig.AppScreenResolver.isScreen("")).isTrue();
        assertThat(WebAppConfig.AppScreenResolver.isScreen("icon-192.png")).isFalse();
        assertThat(WebAppConfig.AppScreenResolver.isScreen("api/plans")).isFalse();
        assertThat(WebAppConfig.AppScreenResolver.isScreen("api")).isFalse();
    }
}
