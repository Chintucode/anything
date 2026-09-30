package com.chintu.anything.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.chintu.anything.plan.CreatePlanRequest;
import com.chintu.anything.plan.PlanRepository;
import com.chintu.anything.plan.PlanService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Copying a plan from the database on your laptop into the one the deployed app uses.
 *
 * <p>The "laptop" here is a real H2 file with the real migrations run against it, and
 * the "deployed" database is the test one. What matters is that everything hanging off
 * a plan arrives with it, that nothing is lost in the move, and that running the tool
 * a second time is a no-op rather than a second copy of everything.
 *
 * <p>Unlike the other integration tests, this one can't wrap itself in a transaction and
 * roll it back: the whole point is that one connection writes and another can then see it,
 * which only a real commit does. So it clears up after itself instead — without that, a
 * plan left behind here turns up as an extra row in whichever test happens to run next.
 */
@SpringBootTest
@ActiveProfiles("test")
class ImportLocalDataTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 28);

    @Autowired
    private DataSource target;

    @Autowired
    private PlanService plans;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private ObjectMapper json;

    @TempDir
    Path laptop;

    private String sourceUrl;

    /** A database like the one on your machine: same migrations, one saved plan. */
    @BeforeEach
    void setUpTheLaptopDatabase() throws Exception {
        planRepository.deleteAll();
        sourceUrl = "jdbc:h2:file:" + laptop.resolve("anything") + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        Flyway.configure().dataSource(sourceUrl, "sa", "")
                .locations("classpath:db/migration").load().migrate();

        String text = Files.readString(Path.of("../fixtures/meditation.md"));
        try (Connection source = source()) {
            // Save through the real service, then move the rows it wrote into the laptop copy.
            var saved = plans.create(new CreatePlanRequest(text, START));
            copyEverythingInto(source);
            planRepository.deleteAll();
            assertThat(saved.totalDays()).isEqualTo(21);
        }
    }

    /** Committed rows outlive the test, so take them out again. */
    @AfterEach
    void leaveTheTestDatabaseAsItWasFound() {
        planRepository.deleteAll();
    }

    private Connection source() throws SQLException {
        return DriverManager.getConnection(sourceUrl, "sa", "");
    }

    /** Moves the just-saved plan out of the test database and into the laptop one. */
    private void copyEverythingInto(Connection laptopDb) throws SQLException {
        try (Connection live = target.getConnection()) {
            laptopDb.setAutoCommit(false);
            new ImportLocalData(live, laptopDb, false).run();
            laptopDb.commit();
        }
    }

    private long count(Connection db, String table) throws SQLException {
        try (Statement s = db.createStatement(); ResultSet rs = s.executeQuery("SELECT count(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void tickTheFirstItem(Connection db) throws SQLException {
        try (Statement s = db.createStatement()) {
            s.executeUpdate("INSERT INTO completions (plan_id, item_id, done_on, created_at, actual_reps)"
                    + " SELECT p.id, i.id, DATE '2026-09-28', TIMESTAMP '2026-09-28 06:30:00', 7"
                    + " FROM plan_items i JOIN plan_days d ON d.id = i.day_id"
                    + " JOIN plan_phases f ON f.id = d.phase_id JOIN plans p ON p.id = f.plan_id"
                    + " ORDER BY i.id LIMIT 1");
            s.executeUpdate("INSERT INTO rested_days (plan_id, rested_on, created_at)"
                    + " SELECT id, DATE '2026-09-30', TIMESTAMP '2026-09-30 21:00:00' FROM plans");
        }
    }

    @Test
    void bringsThePlanAndEverythingHangingOffIt() throws Exception {
        try (Connection source = source()) {
            tickTheFirstItem(source);

            try (Connection live = target.getConnection()) {
                live.setAutoCommit(false);
                new ImportLocalData(source, live, false).run();
                live.commit();

                assertThat(count(live, "plans")).isEqualTo(1);
                assertThat(count(live, "plan_phases")).isEqualTo(count(source, "plan_phases"));
                assertThat(count(live, "plan_days")).isEqualTo(21);
                assertThat(count(live, "plan_items")).isEqualTo(25);
                assertThat(count(live, "completions")).isEqualTo(1);
                assertThat(count(live, "rested_days")).isEqualTo(1);
            }
        }

        var here = plans.get(plans.list().get(0).id());
        assertThat(here.title()).isEqualTo("21-Day Beginner Meditation");
        assertThat(here.startDate()).isEqualTo(START);
        assertThat(here.totalDays()).isEqualTo(21);
        assertThat(here.phases()).hasSize(3);
        // The day's own instructions and its number, not just its name.
        var day1 = here.phases().get(0).days().get(0);
        assertThat(day1.dayNumber()).isEqualTo(1);
        assertThat(day1.weekday()).isNull();
        assertThat(day1.description()).startsWith("Sit comfortably");
        assertThat(day1.items().get(0).name()).isEqualTo("Breath awareness");
    }

    @Test
    void whatYouLoggedKeepsItsDateAndItsNumber() throws Exception {
        try (Connection source = source()) {
            tickTheFirstItem(source);
            try (Connection live = target.getConnection()) {
                live.setAutoCommit(false);
                new ImportLocalData(source, live, false).run();
                live.commit();

                try (Statement s = live.createStatement();
                        ResultSet rs = s.executeQuery(
                                "SELECT done_on, created_at, actual_reps FROM completions")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo("2026-09-28");
                    assertThat(rs.getString(2)).startsWith("2026-09-28 06:30:00");
                    assertThat(rs.getInt(3)).isEqualTo(7);
                }
                try (Statement s = live.createStatement();
                        ResultSet rs = s.executeQuery("SELECT rested_on FROM rested_days")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo("2026-09-30");
                }
            }
        }
    }

    @Test
    void aTickPointsAtTheItemItBelongsTo() throws Exception {
        try (Connection source = source()) {
            tickTheFirstItem(source);
            try (Connection live = target.getConnection()) {
                live.setAutoCommit(false);
                new ImportLocalData(source, live, false).run();
                live.commit();

                // The ids are new, so the tick must have been repointed, not carried over.
                try (Statement s = live.createStatement();
                        ResultSet rs = s.executeQuery("SELECT i.name, d.day_number FROM completions c"
                                + " JOIN plan_items i ON i.id = c.item_id"
                                + " JOIN plan_days d ON d.id = i.day_id")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo("Breath awareness");
                    assertThat(rs.getInt(2)).isEqualTo(1);
                }
            }
        }
    }

    @Test
    void runningItTwiceDoesNotGiveYouTwoCopies() throws Exception {
        try (Connection source = source(); Connection live = target.getConnection()) {
            live.setAutoCommit(false);
            new ImportLocalData(source, live, false).run();
            live.commit();
            new ImportLocalData(source, live, false).run();
            live.commit();

            assertThat(count(live, "plans")).isEqualTo(1);
            assertThat(count(live, "plan_items")).isEqualTo(25);
        }
    }

    @Test
    void aDryRunWritesNothing() throws Exception {
        try (Connection source = source(); Connection live = target.getConnection()) {
            live.setAutoCommit(false);
            new ImportLocalData(source, live, true).run();

            assertThat(count(live, "plans")).isZero();
            assertThat(count(live, "plan_items")).isZero();
        }
    }

    @Test
    void theAppReadsTheCopiedPlanAsItsOwn() throws Exception {
        try (Connection source = source(); Connection live = target.getConnection()) {
            live.setAutoCommit(false);
            new ImportLocalData(source, live, false).run();
            live.commit();
        }
        var detail = plans.list().get(0);
        assertThat(detail.schedule().name()).isEqualTo("SEQUENTIAL");
        assertThat(detail.totalDays()).isEqualTo(21);
        assertThat(json.writeValueAsString(detail)).contains("21-Day Beginner Meditation");
    }
}
