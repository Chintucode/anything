package com.chintu.anything.parser;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a plan written in the Anything format into a {@link ParsedPlan}.
 *
 * <p>Reads the header block, phase headings ("## Weeks 1-4: Name"),
 * day headings ("### Mon: Push") and exercise lines ("- Push-ups | 3x10 | rest 60s").
 * Whole-plan checks (week coverage, duplicate days) are in {@link PlanValidator}.
 *
 * <p>The parser never throws on bad input. It collects every problem with a
 * line number so the user can fix the whole plan at once.
 */
public class PlanParser {

    public static final int SUPPORTED_FORMAT_VERSION = 1;
    public static final int MAX_WEEKS = 52;

    /** A sequential course longer than this is almost certainly a parsing mistake. */
    public static final int MAX_DAYS = 400;

    /** Room for a real instruction, not an essay. */
    public static final int MAX_DESCRIPTION_LENGTH = 1000;

    /** The --- lines around the header. AI models often write a longer row of dashes, so accept 3 or more. */
    private static final Pattern HEADER_FENCE = Pattern.compile("^-{3,}$");
    /** Required header keys, in the order errors are reported. "weeks" or "days" is checked separately. */
    private static final List<String> REQUIRED_KEYS = List.of("anything", "title", "category");

    /**
     * "## Weeks 1-4: Foundation", "## Week 3: Deload", "## Days 1-7: Showing Up" — and
     * "## Week 1 — Showing Up", with the dash every model reached for when asked for a
     * meditation course. (A plain hyphen can't be a separator: it's already the range.)
     */
    private static final Pattern PHASE_HEADING = Pattern.compile(
            "^##\\s+(Weeks?|Days?)\\s+(\\d+)(?:\\s*-\\s*(\\d+))?\\s*(?:[:\\u2014\\u2013]\\s*(.*))?$",
            Pattern.CASE_INSENSITIVE);

    /** "### Mon: Push", "### Monday", "### thu: Legs + Core". */
    private static final Pattern DAY_HEADING = Pattern.compile(
            "^###\\s+(mon|tue|wed|thu|fri|sat|sun)[a-z]*\\s*(?::\\s*(.*))?$",
            Pattern.CASE_INSENSITIVE);

    /** "### Day 9: Naming Thoughts", "### Day 1 - Just Breathe", "### Day 21". */
    private static final Pattern DAY_NUMBER_HEADING = Pattern.compile(
            "^###\\s+Day\\s+(\\d+)\\s*(?:[:\\u2014\\u2013-]\\s*(.*))?$",
            Pattern.CASE_INSENSITIVE);

    /** Any "###" heading at all, used to work out how the plan is scheduled. */
    private static final Pattern ANY_DAY_HEADING = Pattern.compile("^###\\s+\\S.*$");

    /** A category is a label, not a behaviour: letters, digits, spaces and hyphens. */
    private static final Pattern CATEGORY = Pattern.compile("^[a-z0-9][a-z0-9 -]{0,39}$");

    /**
     * "3x10", "3 x 30s", "4×8 each leg", "1xmax" — and the longhand a model reaches
     * for when it forgets the format: "3 sets x 10", "3 sets of 10".
     */
    private static final Pattern SETS_REPS = Pattern.compile(
            "^(\\d+)\\s*(?:sets?)?\\s*(?:[x×]|of)\\s*(\\S.*)$", Pattern.CASE_INSENSITIVE);

    /** A trailing "reps" is noise: "10 reps" and "10" are the same thing. */
    private static final Pattern TRAILING_REPS = Pattern.compile("\\s+reps?$", Pattern.CASE_INSENSITIVE);

    /** A markdown code fence, which models like to wrap the whole answer in. */
    private static final Pattern CODE_FENCE = Pattern.compile("^\\s*(?:`{3,}|~{3,}).*$");

    /** "10", "10 each leg", "30s", "2m", "45 sec each side". */
    private static final Pattern REPS_VALUE = Pattern.compile(
            "^(\\d+)\\s*(s|sec|secs|seconds|m|min|mins|minutes)?(?:\\s+(.+))?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern REST_FIELD = Pattern.compile(
            "^rest\\s+(\\d+)\\s*(s|sec|secs|seconds|m|min|mins|minutes)?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern NOTE_FIELD = Pattern.compile("^note\\s*:\\s*(.*)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern NUMBERED_BULLET = Pattern.compile("^\\d+[.)]\\s+");
    private static final Pattern CHECKBOX = Pattern.compile("^\\[[ xX]\\]\\s*");

    private static final Map<String, DayOfWeek> WEEKDAYS = Map.of(
            "mon", DayOfWeek.MONDAY, "tue", DayOfWeek.TUESDAY, "wed", DayOfWeek.WEDNESDAY,
            "thu", DayOfWeek.THURSDAY, "fri", DayOfWeek.FRIDAY, "sat", DayOfWeek.SATURDAY,
            "sun", DayOfWeek.SUNDAY);

    public static final int MAX_SETS = 20;

    /** Longest line accepted. Keeps every stored field within its database column. */
    public static final int MAX_LINE_LENGTH = 500;

    private static final Pattern HEADER_LINE = Pattern.compile("^([A-Za-z][A-Za-z0-9_-]*)\\s*:\\s*(.*)$");

    public ParseResult parse(String markdown) {
        List<ParseError> errors = new ArrayList<>();

        if (markdown == null || markdown.isBlank()) {
            errors.add(new ParseError(1, "The plan is empty. Paste a plan that starts with a --- header block."));
            return ParseResult.failure(errors);
        }

        // Handles Windows (\r\n) and old Mac (\r) line endings too.
        String[] lines = markdown.split("\\r\\n|\\r|\\n", -1);

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].length() > MAX_LINE_LENGTH) {
                errors.add(new ParseError(i + 1,
                        "This line is too long (over " + MAX_LINE_LENGTH + " characters). Shorten the name or note."));
            }
        }
        if (!errors.isEmpty()) {
            return ParseResult.failure(errors);
        }

        // Cosmetic noise a model adds by reflex, removed in place so line numbers
        // in error messages still point at what the person is looking at.
        for (int i = 0; i < lines.length; i++) {
            lines[i] = tidy(lines[i]);
        }

        int bodyStart = 0;
        PlanHeader header = null;

        // Which kind of plan this is decides how every heading below reads, so it's
        // settled first, from the day headings themselves rather than from a header
        // field a model might forget.
        PlanHeader.Schedule schedule = detectSchedule(lines, errors);
        if (schedule == null) {
            return ParseResult.failure(errors);
        }

        int first = firstNonBlank(lines, 0);
        if (first >= 0 && isFence(lines[first])) {
            int close = findHeaderClose(lines, first + 1);
            if (close < 0) {
                errors.add(new ParseError(first + 1,
                        "The header block is never closed. Add a line with just --- after the last header field."));
                return ParseResult.failure(errors);
            }
            header = parseHeader(lines, first + 1, close, first + 1, schedule, errors);
            bodyStart = close + 1;
        } else {
            errors.add(new ParseError(first + 1,
                    "The plan must start with a header block: a line with ---, then anything, title, category and weeks (or days), then ---."));
            bodyStart = Math.max(first, 0);
        }

        List<ParsedPhase> phases = parseBody(lines, bodyStart, header, schedule, errors);

        PlanValidator.checkDuplicateDays(phases, errors);
        // Coverage only makes sense once every heading parsed; otherwise a skipped
        // phase would show up as a confusing "weeks not covered" error.
        if (errors.isEmpty() && header != null && !phases.isEmpty()) {
            phases = PlanValidator.checkCoverage(header, phases, errors);
        }

        if (phases.isEmpty() && errors.isEmpty()) {
            errors.add(new ParseError(bodyStart + 1, schedule == PlanHeader.Schedule.SEQUENTIAL
                    ? "No phases found. Add at least one heading like \"## Days 1-7: Showing Up\"."
                    : "No phases found. Add at least one heading like \"## Weeks 1-4: Foundation\"."));
        }

        return errors.isEmpty()
                ? ParseResult.success(new ParsedPlan(header, phases))
                : ParseResult.failure(errors);
    }

    /**
     * Weekly or sequential, decided by the day headings: "### Mon" means one,
     * "### Day 9" the other. A plan that uses both is an error rather than a guess —
     * the two schedules behave differently enough that picking wrong would be worse
     * than saying so. A plan with no day headings at all defaults to weekly, and
     * fails later on the errors it really has.
     */
    private static PlanHeader.Schedule detectSchedule(String[] lines, List<ParseError> errors) {
        int weekdayLine = -1;
        int numberedLine = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!ANY_DAY_HEADING.matcher(line).matches()) {
                continue;
            }
            if (DAY_NUMBER_HEADING.matcher(line).matches()) {
                numberedLine = numberedLine < 0 ? i + 1 : numberedLine;
            } else if (DAY_HEADING.matcher(line).matches()) {
                weekdayLine = weekdayLine < 0 ? i + 1 : weekdayLine;
            }
        }

        if (weekdayLine > 0 && numberedLine > 0) {
            errors.add(new ParseError(Math.max(weekdayLine, numberedLine),
                    "This plan mixes weekdays (line " + weekdayLine + ") and day numbers (line "
                            + numberedLine + "). Use one or the other: weekdays for a weekly "
                            + "program, \"Day 1\", \"Day 2\" for a course you work through in order."));
            return null;
        }
        return numberedLine > 0 ? PlanHeader.Schedule.SEQUENTIAL : PlanHeader.Schedule.WEEKLY;
    }

    // ---------------------------------------------------------------- header

    private PlanHeader parseHeader(String[] lines, int from, int to, int openingLine,
            PlanHeader.Schedule schedule, List<ParseError> errors) {
        Map<String, String> values = new HashMap<>();
        Map<String, Integer> lineOf = new HashMap<>();

        for (int i = from; i < to; i++) {
            String raw = lines[i].trim();
            if (raw.isEmpty()) {
                continue;
            }
            Matcher m = HEADER_LINE.matcher(raw);
            if (!m.matches()) {
                errors.add(new ParseError(i + 1, "Header lines must look like \"key: value\"."));
                continue;
            }
            String key = m.group(1).toLowerCase();
            String value = m.group(2).trim();
            if (values.containsKey(key)) {
                errors.add(new ParseError(i + 1, "\"" + key + "\" appears twice in the header."));
                continue;
            }
            values.put(key, value);
            lineOf.put(key, i + 1);
            // Unknown keys are ignored on purpose, so newer plans still open in older app versions.
        }

        int errorsBefore = errors.size();
        for (String key : REQUIRED_KEYS) {
            if (values.getOrDefault(key, "").isEmpty()) {
                errors.add(new ParseError(openingLine, "The header is missing \"" + key + "\"."));
            }
        }
        if (errors.size() > errorsBefore) {
            return null;
        }

        Integer version = parsePositiveInt(values.get("anything"));
        if (version == null || version != SUPPORTED_FORMAT_VERSION) {
            errors.add(new ParseError(lineOf.get("anything"),
                    "Unsupported format version \"" + values.get("anything") + "\". Use \"anything: 1\"."));
        }

        // A category is a label the person reads, not a switch the app flips, so any
        // sensible word will do: workout, meditation, study, whatever the plan is.
        String category = values.get("category").toLowerCase();
        if (!CATEGORY.matcher(category).matches()) {
            errors.add(new ParseError(lineOf.get("category"),
                    "Category \"" + values.get("category")
                            + "\" should be a short word or two, like \"workout\" or \"meditation\"."));
        }

        boolean sequential = schedule == PlanHeader.Schedule.SEQUENTIAL;
        String lengthKey = sequential ? "days" : "weeks";
        String otherKey = sequential ? "weeks" : "days";
        int max = sequential ? MAX_DAYS : MAX_WEEKS;

        if (!values.getOrDefault(otherKey, "").isEmpty() && values.getOrDefault(lengthKey, "").isEmpty()) {
            errors.add(new ParseError(lineOf.get(otherKey), sequential
                    ? "This plan is written as \"Day 1\", \"Day 2\", so the header needs \"days: <total>\" instead of \"weeks\"."
                    : "This plan is written with weekdays, so the header needs \"weeks: <total>\" instead of \"days\"."));
            return null;
        }

        if (values.getOrDefault(lengthKey, "").isEmpty()) {
            errors.add(new ParseError(openingLine, "The header is missing \"" + lengthKey + "\"."));
            return null;
        }
        Integer length = parsePositiveInt(values.get(lengthKey));
        if (length == null || length > max) {
            errors.add(new ParseError(lineOf.getOrDefault(lengthKey, openingLine),
                    "\"" + lengthKey + "\" must be a whole number from 1 to " + max + "."));
        }

        if (errors.size() > errorsBefore) {
            return null;
        }
        return new PlanHeader(version, values.get("title"), category, schedule, length);
    }

    // ------------------------------------------------------------------ body

    /**
     * Walks the plan below the header, building phases, days and exercises.
     *
     * <p>When a heading is invalid, the lines under it are skipped quietly,
     * so one mistake doesn't produce a cascade of follow-on errors.
     */
    private List<ParsedPhase> parseBody(String[] lines, int from, PlanHeader header,
            PlanHeader.Schedule schedule, List<ParseError> errors) {
        List<ParsedPhase> phases = new ArrayList<>();

        PhaseBuilder phase = null;       // current valid phase, or null
        boolean inBrokenPhase = false;   // under an invalid "##" heading
        DayBuilder day = null;           // current valid day, or null
        boolean inBrokenDay = false;     // under an invalid "###" heading

        for (int i = from; i < lines.length; i++) {
            String line = lines[i].trim();
            int lineNo = i + 1;

            if (line.startsWith("###")) {
                day = null;
                inBrokenDay = false;
                if (phase == null) {
                    if (!inBrokenPhase) {
                        errors.add(new ParseError(lineNo,
                                "Day headings must come after a phase heading like \"## Weeks 1-4: Foundation\"."));
                    }
                    inBrokenDay = true;
                    continue;
                }
                day = parseDayHeading(line, lineNo, schedule, phase, header, errors);
                if (day == null) {
                    inBrokenDay = true;
                    phase.hadBrokenDay = true;
                } else {
                    phase.days.add(day);
                }

            } else if (line.startsWith("##")) {
                closePhase(phase, phases, schedule, errors);
                phase = parsePhaseHeading(line, lineNo, header, schedule, errors);
                inBrokenPhase = phase == null;
                day = null;
                inBrokenDay = false;

            } else if (isBullet(line)) {
                if (inBrokenPhase || inBrokenDay) {
                    continue;
                }
                if (day == null) {
                    errors.add(new ParseError(lineNo,
                            "Exercises must come after a day heading like \"### Mon: Push\"."));
                    continue;
                }
                ParsedItem item = parseItem(stripBullet(line), lineNo, errors);
                if (item != null) {
                    day.items.add(item);
                }

            } else if (!line.isEmpty() && !inBrokenPhase && !inBrokenDay) {
                // Prose under a heading, before its first item. On a meditation day this
                // is the practice itself, so it's kept rather than skipped. Anything
                // after the first item is still ignored — by then the day has shape.
                if (day != null && day.items.isEmpty()) {
                    day.description.add(line);
                } else if (day == null && phase != null && phase.days.isEmpty()) {
                    phase.description.add(line);
                }
            }
        }
        closePhase(phase, phases, schedule, errors);
        return phases;
    }

    private PhaseBuilder parsePhaseHeading(String line, int lineNo, PlanHeader header,
            PlanHeader.Schedule schedule, List<ParseError> errors) {
        boolean sequential = schedule == PlanHeader.Schedule.SEQUENTIAL;
        Matcher m = PHASE_HEADING.matcher(line);
        if (!m.matches()) {
            errors.add(new ParseError(lineNo, sequential
                    ? "Phase headings must look like \"## Days 1-7: Name\" or \"## Week 2: Name\"."
                    : "Phase headings must look like \"## Weeks 1-4: Name\" or \"## Week 5: Name\"."));
            return null;
        }

        boolean writtenInWeeks = m.group(1).toLowerCase().startsWith("week");
        int from = Integer.parseInt(m.group(2));
        int to = m.group(3) != null ? Integer.parseInt(m.group(3)) : from;
        String name = m.group(4) != null ? m.group(4).trim() : "";
        String unit = sequential ? "Day" : "Week";

        if (!sequential && !writtenInWeeks) {
            errors.add(new ParseError(lineNo,
                    "This plan is scheduled by weekday, so its phases are weeks: \"## Weeks " + from + "-" + to + ": Name\"."));
            return null;
        }
        if (from < 1) {
            errors.add(new ParseError(lineNo, unit + "s start at 1, not " + from + "."));
            return null;
        }
        if (from > to) {
            errors.add(new ParseError(lineNo,
                    unit + " range " + from + "-" + to + " is backwards. Write it as " + to + "-" + from + "."));
            return null;
        }
        // Models group a 21-day course into "Week 1", "Week 2", "Week 3" out of habit.
        // That's a perfectly clear thing to mean, so read it as days 1-7, 8-14, 15-21.
        if (sequential && writtenInWeeks) {
            to = to * 7;
            from = from * 7 - 6;
            if (header != null) {
                to = Math.min(to, header.length());
            }
        }
        if (header != null && to > header.length()) {
            errors.add(new ParseError(lineNo, unit + " " + to + " is past the end of the plan ("
                    + (sequential ? "days: " : "weeks: ") + header.length() + ")."));
            return null;
        }
        return new PhaseBuilder(from, to, name, lineNo);
    }

    private DayBuilder parseDayHeading(String line, int lineNo, PlanHeader.Schedule schedule,
            PhaseBuilder phase, PlanHeader header, List<ParseError> errors) {
        if (schedule == PlanHeader.Schedule.SEQUENTIAL) {
            return parseNumberedDay(line, lineNo, phase, header, errors);
        }

        Matcher m = DAY_HEADING.matcher(line);
        if (!m.matches()) {
            errors.add(new ParseError(lineNo,
                    "Day headings must look like \"### Mon: Push\" (Mon, Tue, Wed, Thu, Fri, Sat or Sun)."));
            return null;
        }
        DayOfWeek weekday = WEEKDAYS.get(m.group(1).substring(0, 3).toLowerCase());
        String title = m.group(2) != null ? m.group(2).trim() : "";
        return new DayBuilder(weekday, null, title, lineNo);
    }

    /** "### Day 9: Naming Thoughts" — the ninth thing you do, not a date. */
    private DayBuilder parseNumberedDay(String line, int lineNo, PhaseBuilder phase,
            PlanHeader header, List<ParseError> errors) {
        Matcher m = DAY_NUMBER_HEADING.matcher(line);
        if (!m.matches()) {
            errors.add(new ParseError(lineNo,
                    "Day headings must look like \"### Day 9: Naming Thoughts\"."));
            return null;
        }
        int number = Integer.parseInt(m.group(1));
        String title = m.group(2) != null ? m.group(2).trim() : "";

        if (number < 1) {
            errors.add(new ParseError(lineNo, "Days start at 1, not " + number + "."));
            return null;
        }
        if (header != null && number > header.length()) {
            errors.add(new ParseError(lineNo,
                    "Day " + number + " is past the end of the plan (days: " + header.length() + ")."));
            return null;
        }
        if (phase != null && (number < phase.from || number > phase.to)) {
            errors.add(new ParseError(lineNo, "Day " + number + " isn't inside this phase (days "
                    + phase.from + "-" + phase.to + "). Move it under the phase it belongs to."));
            return null;
        }
        return new DayBuilder(null, number, title, lineNo);
    }

    private ParsedItem parseItem(String text, int lineNo, List<ParseError> errors) {
        String[] fields = text.split("\\|", -1);
        String name = fields[0].trim();

        if (name.isEmpty()) {
            errors.add(new ParseError(lineNo, "This exercise has no name before the first |."));
            return null;
        }
        if (fields.length < 2 || fields[1].isBlank()) {
            errors.add(new ParseError(lineNo,
                    "\"" + name + "\" needs sets and reps, like \"" + name + " | 3x10\"."));
            return null;
        }

        // "3x10 reps" and "3x10" are the same plan; the word adds nothing to track.
        String amount = TRAILING_REPS.matcher(fields[1].trim()).replaceFirst("").trim();
        Matcher sr = SETS_REPS.matcher(amount);
        int sets;
        String repsText;

        if (sr.matches()) {
            sets = Integer.parseInt(sr.group(1));
            repsText = sr.group(2).trim();
        } else if (isBareAmount(amount)) {
            // "10m" is one block of ten minutes, not a line missing its sets. Most
            // things that aren't lifting are written this way.
            sets = 1;
            repsText = amount;
        } else {
            errors.add(new ParseError(lineNo, "\"" + fields[1].trim()
                    + "\" isn't sets x reps or a length. Write it like 3x10, 3x30s, 1xmax or 10m."));
            return null;
        }

        if (sets < 1 || sets > MAX_SETS) {
            errors.add(new ParseError(lineNo, "Sets must be from 1 to " + MAX_SETS + ", not " + sets + "."));
            return null;
        }
        Reps reps = parseReps(repsText);

        Integer rest = null;
        String note = "";
        boolean ok = true;
        for (int f = 2; f < fields.length; f++) {
            String field = fields[f].trim();
            if (field.isEmpty()) {
                continue;
            }
            Matcher restM = REST_FIELD.matcher(field);
            Matcher noteM = NOTE_FIELD.matcher(field);
            if (restM.matches()) {
                rest = toSeconds(Integer.parseInt(restM.group(1)), restM.group(2));
            } else if (noteM.matches()) {
                note = noteM.group(1).trim();
            } else {
                errors.add(new ParseError(lineNo,
                        "Unknown field \"" + field + "\". After sets x reps, use \"rest 60s\" or \"note: your tip\"."));
                ok = false;
            }
        }
        return ok ? new ParsedItem(name, sets, reps, rest, note, lineNo) : null;
    }

    /** A number with an optional unit, or "max": enough to stand alone without sets. */
    private static boolean isBareAmount(String text) {
        return text.equalsIgnoreCase("max") || REPS_VALUE.matcher(text).matches();
    }

    private static Reps parseReps(String raw) {
        if (raw.equalsIgnoreCase("max")) {
            return new Reps(Reps.Kind.MAX, null, "", raw);
        }
        Matcher m = REPS_VALUE.matcher(raw);
        if (m.matches()) {
            int number = Integer.parseInt(m.group(1));
            String unit = m.group(2);
            String detail = m.group(3) == null ? "" : m.group(3).trim();
            return unit == null
                    ? new Reps(Reps.Kind.COUNT, number, detail, raw)
                    : new Reps(Reps.Kind.SECONDS, toSeconds(number, unit), detail, raw);
        }
        return new Reps(Reps.Kind.TEXT, null, "", raw);
    }

    private static int toSeconds(int number, String unit) {
        if (unit == null) {
            return number; // "rest 90" means 90 seconds
        }
        return unit.toLowerCase().startsWith("m") ? number * 60 : number;
    }

    private void closePhase(PhaseBuilder phase, List<ParsedPhase> phases,
            PlanHeader.Schedule schedule, List<ParseError> errors) {
        if (phase == null) {
            return;
        }
        List<ParsedDay> days = new ArrayList<>();
        for (DayBuilder d : phase.days) {
            if (d.items.isEmpty()) {
                errors.add(new ParseError(d.line,
                        "This day has no exercises. Add lines like \"- Push-ups | 3x10\", or remove the day to make it a rest day."));
            }
            days.add(new ParsedDay(d.weekday, d.dayNumber, d.title,
                    description(d.description, d.line, errors), d.line, d.items));
        }
        // One mistake, one error: if this phase is empty only because its day heading
        // was already rejected, saying "no days" as well would just repeat it.
        if (days.isEmpty() && !phase.hadBrokenDay) {
            errors.add(new ParseError(phase.line, schedule == PlanHeader.Schedule.SEQUENTIAL
                    ? "This phase has no days. Add a day like \"### Day 1: Just Breathe\" under it."
                    : "This phase has no training days. Add a day like \"### Mon: Push\" under it."));
        }
        phases.add(new ParsedPhase(phase.from, phase.to, phase.name,
                description(phase.description, phase.line, errors), phase.line, days));
    }

    /**
     * Strips what a model decorates its answer with: a code fence around the plan,
     * and bold or italic marks on headings and exercise names. Neither means
     * anything here, and both used to turn a good plan into a page of errors.
     */
    private static String tidy(String line) {
        if (CODE_FENCE.matcher(line).matches()) {
            return "";   // blank, so it's skipped like any other empty line
        }
        return line.replace("**", "").replace("__", "");
    }

    private static boolean isBullet(String line) {
        return line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ")
                || NUMBERED_BULLET.matcher(line).lookingAt();
    }

    /** Removes "- ", "* ", "1. " and an optional checkbox like "[ ]" or "[x]". */
    private static String stripBullet(String line) {
        String text = NUMBERED_BULLET.matcher(line).lookingAt()
                ? NUMBERED_BULLET.matcher(line).replaceFirst("")
                : line.substring(2);
        return CHECKBOX.matcher(text.trim()).replaceFirst("");
    }

    /** Mutable holders used while walking the lines; turned into records at the end. */
    private static final class PhaseBuilder {
        final int from;
        final int to;
        final String name;
        final int line;
        final List<String> description = new ArrayList<>();
        final List<DayBuilder> days = new ArrayList<>();
        /** A day heading under this phase was rejected, so "no days" would only repeat that error. */
        boolean hadBrokenDay;

        PhaseBuilder(int from, int to, String name, int line) {
            this.from = from;
            this.to = to;
            this.name = name;
            this.line = line;
        }
    }

    private static final class DayBuilder {
        final DayOfWeek weekday;
        final Integer dayNumber;
        final String title;
        final int line;
        final List<String> description = new ArrayList<>();
        final List<ParsedItem> items = new ArrayList<>();

        DayBuilder(DayOfWeek weekday, Integer dayNumber, String title, int line) {
            this.weekday = weekday;
            this.dayNumber = dayNumber;
            this.title = title;
            this.line = line;
        }
    }

    /**
     * Joins the prose collected under a heading, and refuses an essay. The limit is
     * generous enough for a full meditation instruction and small enough to store.
     */
    private static String description(List<String> lines, int lineNo, List<ParseError> errors) {
        String text = String.join(" ", lines).trim();
        if (text.length() > MAX_DESCRIPTION_LENGTH) {
            errors.add(new ParseError(lineNo, "This description is over " + MAX_DESCRIPTION_LENGTH
                    + " characters. Trim it, or move the detail into a note on an item."));
            return text.substring(0, MAX_DESCRIPTION_LENGTH);
        }
        return text;
    }

    // --------------------------------------------------------------- helpers

    private static int firstNonBlank(String[] lines, int from) {
        for (int i = from; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                return i;
            }
        }
        return -1;
    }

    private static int findHeaderClose(String[] lines, int from) {
        for (int i = from; i < lines.length; i++) {
            if (isFence(lines[i])) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isFence(String line) {
        return HEADER_FENCE.matcher(line.trim()).matches();
    }

    private static Integer parsePositiveInt(String value) {
        try {
            int n = Integer.parseInt(value.trim());
            return n > 0 ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
