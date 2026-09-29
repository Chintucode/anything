package com.chintu.anything.config;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * The database when the app runs on the internet (the {@code prod} profile).
 *
 * <p>It reads one environment variable, {@code DATABASE_URL}, holding the connection
 * string exactly as the database host shows it. See {@link DatabaseUrl}.
 *
 * <p>The pool is sized for a free database that goes to sleep when nobody is using it.
 * Neon suspends after five idle minutes, and every minute awake counts against the
 * free allowance. A pool that kept connections open forever would keep it awake
 * forever, so idle connections are let go after a minute and none are kept in reserve.
 */
@Configuration
@Profile("prod")
public class ProductionDatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(ProductionDatabaseConfig.class);

    @Bean
    public DataSource dataSource(@Value("${DATABASE_URL:}") String databaseUrl) {
        DatabaseUrl url = DatabaseUrl.parse(databaseUrl);
        log.info("Database: {}", url);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url.jdbcUrl());
        config.setUsername(url.username());
        config.setPassword(url.password());
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(0);
        config.setIdleTimeout(60_000);
        // A sleeping database takes a few seconds to wake; don't give up before it does.
        config.setConnectionTimeout(20_000);
        config.setPoolName("anything");
        return new HikariDataSource(config);
    }
}
