package com.chintu.anything.tools;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.chintu.anything.config.DatabaseUrl;

/**
 * Copies the plans on your own machine into the database the deployed app uses.
 *
 * <p>Run it once, after deploying, so the plans you have been keeping locally —
 * and every tick, rest day and logged number that goes with them — carry on where
 * they left off instead of starting again from an empty screen.
 *
 * <pre>
 *   DATABASE_URL='postgresql://...' tools/import-local-data.sh
 *   DATABASE_URL='postgresql://...' tools/import-local-data.sh --dry-run
 * </pre>
 *
 * <p><b>It never overwrites and never doubles up.</b> A plan is recognised by its
 * title and start date, and one already in the target is left exactly as it is —
 * so running this twice changes nothing the second time, and a plan you have
 * since edited online is not clobbered by the older copy on your laptop.
 *
 * <p>Row ids are not carried across. The target assigns its own, and everything
 * pointing at a row is repointed as it goes.
 */
public final class ImportLocalData {

    private final Connection source;
    private final Connection target;
    private final boolean dryRun;

    ImportLocalData(Connection source, Connection target, boolean dryRun) {
        this.source = source;
        this.target = target;
        this.dryRun = dryRun;
    }

    public static void main(String[] args) throws Exception {
        boolean dryRun = false;
        String h2Path = "./data/anything";
        for (String arg : args) {
            if (arg.equals("--dry-run")) {
                dryRun = true;
            } else if (arg.startsWith("--from=")) {
                h2Path = arg.substring("--from=".length());
            } else {
                System.err.println("Unknown option: " + arg);
                System.err.println("Usage: import-local-data.sh [--dry-run] [--from=<path to the .mv.db file, without that suffix>]");
                System.exit(2);
            }
        }

        DatabaseUrl url;
        try {
            url = DatabaseUrl.parse(System.getenv("DATABASE_URL"));
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println("Run it like this, with your database's connection string in quotes:");
            System.err.println("  DATABASE_URL='postgresql://...' tools/import-local-data.sh");
            System.exit(2);
            return;
        }

        // Read-only, so nothing here can damage the copy on your machine.
        String h2 = "jdbc:h2:file:" + h2Path
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;ACCESS_MODE_DATA=r";

        System.out.println("From: " + h2Path + " (on this machine, read-only)");
        System.out.println("To:   " + url);
        System.out.println(dryRun ? "Dry run: nothing will be written.\n" : "");

        try (Connection source = DriverManager.getConnection(h2, "sa", "");
                Connection target = DriverManager.getConnection(url.jdbcUrl(), url.username(), url.password())) {
            target.setAutoCommit(false);
            new ImportLocalData(source, target, dryRun).run();
        }
    }

    // ------------------------------------------------------------------ the copy

    private record LocalPlan(long id, String title, String startDate) { }

    void run() throws SQLException {
        List<LocalPlan> here = plansIn(source);
        List<LocalPlan> there = plansIn(target);
        if (here.isEmpty()) {
            System.out.println("There are no plans on this machine to copy.");
            return;
        }

        int copied = 0;
        for (LocalPlan plan : here) {
            boolean already = there.stream()
                    .anyMatch(p -> p.title().equals(plan.title()) && p.startDate().equals(plan.startDate()));
            if (already) {
                System.out.printf("Already there, left alone: %s (from %s)%n", plan.title(), plan.startDate());
                continue;
            }
            copyPlan(plan);
            copied++;
        }

        if (dryRun) {
            target.rollback();
            System.out.printf("%nDry run finished. %d plan(s) would be copied. Nothing was written.%n", copied);
            return;
        }
        target.commit();
        System.out.printf("%nDone. %d plan(s) copied. Open the app and pull down to refresh.%n", copied);
    }

    private static List<LocalPlan> plansIn(Connection db) throws SQLException {
        List<LocalPlan> plans = new ArrayList<>();
        try (Statement s = db.createStatement();
                ResultSet rs = s.executeQuery("SELECT id, title, start_date FROM plans ORDER BY id")) {
            while (rs.next()) {
                plans.add(new LocalPlan(rs.getLong(1), rs.getString(2), rs.getString(3)));
            }
        }
        return plans;
    }

    private void copyPlan(LocalPlan plan) throws SQLException {
        long planId = copyRow(
                "SELECT title, category, weeks, start_date, day_offset, raw_markdown, created_at, schedule, total_days"
                        + " FROM plans WHERE id = ?",
                plan.id(),
                "INSERT INTO plans (title, category, weeks, start_date, day_offset, raw_markdown, created_at,"
                        + " schedule, total_days) VALUES (?,?,?,?,?,?,?,?,?)",
                9).get(0);

        Map<Long, Long> items = new HashMap<>();
        int dayCount = 0;
        int itemCount = 0;

        for (long oldPhase : idsOf("SELECT id FROM plan_phases WHERE plan_id = ? ORDER BY id", plan.id())) {
            long phaseId = copyRow(
                    "SELECT from_week, to_week, name, description FROM plan_phases WHERE id = ?", oldPhase,
                    "INSERT INTO plan_phases (plan_id, from_week, to_week, name, description) VALUES (?,?,?,?,?)",
                    4, planId).get(0);

            for (long oldDay : idsOf("SELECT id FROM plan_days WHERE phase_id = ? ORDER BY id", oldPhase)) {
                long dayId = copyRow(
                        "SELECT weekday, title, sort_order, day_number, description FROM plan_days WHERE id = ?",
                        oldDay,
                        "INSERT INTO plan_days (phase_id, weekday, title, sort_order, day_number, description)"
                                + " VALUES (?,?,?,?,?,?)",
                        5, phaseId).get(0);
                dayCount++;

                for (long oldItem : idsOf("SELECT id FROM plan_items WHERE day_id = ? ORDER BY id", oldDay)) {
                    long itemId = copyRow(
                            "SELECT sort_order, name, set_count, reps_kind, reps_value, reps_detail, reps_raw,"
                                    + " rest_seconds, note FROM plan_items WHERE id = ?",
                            oldItem,
                            "INSERT INTO plan_items (day_id, sort_order, name, set_count, reps_kind, reps_value,"
                                    + " reps_detail, reps_raw, rest_seconds, note) VALUES (?,?,?,?,?,?,?,?,?,?)",
                            9, dayId).get(0);
                    items.put(oldItem, itemId);
                    itemCount++;
                }
            }
        }

        int ticks = 0;
        for (long oldItem : items.keySet().stream().sorted().toList()) {
            ticks += copyRow(
                    "SELECT done_on, created_at, actual_reps FROM completions WHERE item_id = ? ORDER BY done_on",
                    oldItem,
                    "INSERT INTO completions (plan_id, item_id, done_on, created_at, actual_reps) VALUES (?,?,?,?,?)",
                    3, planId, items.get(oldItem)).size();
        }
        int skips = copyRow(
                "SELECT skip_on, created_at FROM skipped_days WHERE plan_id = ? ORDER BY skip_on", plan.id(),
                "INSERT INTO skipped_days (plan_id, skip_on, created_at) VALUES (?,?,?)", 2, planId).size();
        int rests = copyRow(
                "SELECT rested_on, created_at FROM rested_days WHERE plan_id = ? ORDER BY rested_on", plan.id(),
                "INSERT INTO rested_days (plan_id, rested_on, created_at) VALUES (?,?,?)", 2, planId).size();

        System.out.printf("%s: %d days, %d exercises, %d ticked, %d rest day(s), %d skipped%n",
                (dryRun ? "Would copy " : "Copied ") + plan.title(), dayCount, itemCount, ticks, rests, skips);
    }

    /**
     * A date or a time as the plan means it, with no time zone attached.
     *
     * <p>The columns are DATE and TIMESTAMP, which carry no zone. The old JDBC types
     * for them do — they are instants — so copying one straight across would shift it
     * by the difference between the two machines' clocks, and a workout ticked late on
     * Sunday could land on Monday. These types have no zone in them at all.
     */
    private static Object zoneFree(Object value) {
        if (value instanceof java.sql.Timestamp t) {
            return t.toLocalDateTime();
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        return value;
    }

    private List<Long> idsOf(String sql, long parent) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement s = source.prepareStatement(sql)) {
            s.setLong(1, parent);
            try (ResultSet rs = s.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
        }
        return ids;
    }

    /**
     * Reads every row the query returns and inserts each one, putting {@code owners}
     * (the ids this row now belongs to) in front of its own {@code columns} values.
     *
     * @return the id the target gave each new row, in order
     */
    private List<Long> copyRow(String select, long key, String insert, int columns, long... owners)
            throws SQLException {

        List<Long> newIds = new ArrayList<>();
        try (PreparedStatement read = source.prepareStatement(select)) {
            read.setLong(1, key);
            try (ResultSet rs = read.executeQuery()) {
                while (rs.next()) {
                    try (PreparedStatement write =
                            target.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
                        int at = 1;
                        for (long owner : owners) {
                            write.setLong(at++, owner);
                        }
                        for (int c = 1; c <= columns; c++) {
                            write.setObject(at++, zoneFree(rs.getObject(c)));
                        }
                        write.executeUpdate();
                        try (ResultSet keys = write.getGeneratedKeys()) {
                            newIds.add(keys.next() ? keys.getLong(1) : -1L);
                        }
                    }
                }
            }
        }
        return newIds;
    }
}
