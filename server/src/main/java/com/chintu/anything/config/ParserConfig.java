package com.chintu.anything.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.chintu.anything.parser.PlanParser;

/**
 * Registers the parser as a Spring bean. The parser package itself stays free of
 * Spring annotations, so it can be tested (and reused) as plain Java.
 */
@Configuration
public class ParserConfig {

    @Bean
    public PlanParser planParser() {
        return new PlanParser();
    }
}
