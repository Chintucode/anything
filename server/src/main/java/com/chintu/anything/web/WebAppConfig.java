package com.chintu.anything.web;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the web app from the same server as the API, so the whole thing is one URL.
 *
 * <p>The production build copies the web app's {@code dist/} folder into
 * {@code static/} inside the jar. In local development there is no such folder,
 * Vite serves the web app, and nothing here does anything.
 *
 * <ul>
 *   <li><b>/assets/**</b> are Vite's hashed files. Their names change whenever their
 *       content does, so the browser may keep them for a year.</li>
 *   <li><b>Everything else</b> (index.html, the service worker, the manifest) must be
 *       checked every time, or a phone would keep running last week's app.</li>
 *   <li><b>/plans, /plans/new</b> are screens, not files. The browser asks the server
 *       for them on a reload, and the answer is index.html, whose router takes over.</li>
 * </ul>
 */
@Configuration
public class WebAppConfig implements WebMvcConfigurer {

    private static final String STATIC = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(STATIC + "assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());

        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new AppScreenResolver());
    }

    /** Phones decide whether the app can be installed partly from this file's type. */
    @Bean
    WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> manifestMimeType() {
        return factory -> {
            MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);
            mappings.add("webmanifest", "application/manifest+json");
            factory.setMimeMappings(mappings);
        };
    }

    /** Real files as they are; a screen's path gets index.html; anything else is a 404. */
    static final class AppScreenResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String path, Resource location) throws IOException {
            Resource file = location.createRelative(path);
            if (!path.isEmpty() && file.exists() && file.isReadable()) {
                return file;
            }
            if (!isScreen(path)) {
                return null;
            }
            Resource index = location.createRelative("index.html");
            return index.exists() && index.isReadable() ? index : null;
        }

        /**
         * A screen's path looks like "plans/new": no file extension, and never under
         * /api, where a wrong address should be an honest 404, not a web page.
         */
        static boolean isScreen(String path) {
            if (path.equals("api") || path.startsWith("api/")) {
                return false;
            }
            String last = path.substring(path.lastIndexOf('/') + 1);
            return !last.contains(".");
        }
    }
}
