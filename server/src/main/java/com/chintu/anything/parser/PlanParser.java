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
    public static final Set<String> SUPPORTED_CATEGORIES = Set.of("workout");

    /** The --- lines around the header. AI models often write a longer row of dashes, so accept 3 or more. */
    private static final Pattern HEADER_FENCE = Pattern.compile("^-{3,}$");
    /** Required header keys, in the order errors are reported. */
    private static final List<String> REQUIRED_KEYS = List.of("anything", "title", "category", "weeks");

    /** "## Weeks 1-4: Foundation", "## Week 3", "## Week 3: Deload". */
    private static final Pattern PHASE_HEADING = Pattern.compile(
            "^##\\s+Weeks?\\s+(\\d+)(?:\\s*-\\s*(\\d+))?\\s*(?::\\s*(.*))?$",
            Pattern.CASE_INSENSITIVE);

    /** "### Mon: Push", "### Monday", "### thu: Legs + Core". */
    private static final Pattern DAY_HEADING = Pattern.compile(
            "^###\\s+(mon|tue|wed|thu|fri|sat|sun)[a-z]*\\s*(?::\\s*(.*))?$",
            Pattern.CASE_INSENSITIVE);

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

        int first = firstNonBlank(lines, 0);
        if (first >= 0 && isFence(lines[first])) {
            int close = findHeaderClose(lines, first + 1);
            if (close < 0) {
                errors.add(new ParseError(first + 1,
                        "The header block is never closed. Add a line with just --- after the last header field."));
                return ParseResult.failure(errors);
            }
            header = parseHeader(lines, first + 1, close, first + 1, errors);
            bodyStart = close + 1;
        } else {
            errors.add(new ParseError(first + 1,
                    "The plan must start with a header block: a line with ---, then anything, title, category and weeks, then ---."));
            bodyStart = Math.max(first, 0);
        }

        List<ParsedPhase> phases = parseBody(lines, bodyStart, header, errors);

        PlanValidator.checkDuplicateDays(phases, errors);
        // Coverage only makes sense once every heading parsed; otherwise a skipped
        // phase would show up as a confusing "weeks not covered" error.
        if (errors.isEmpty() && header != null && !phases.isEmpty()) {
            phases = PlanValidator.checkWeekCoverage(header, phases, errors);
        }

        if (phases.isEmpty() && errors.isEmpty()) {
            errors.add(new ParseError(bodyStart + 1,
                    "No phases found. Add at least one heading like \"## Weeks 1-4: Foundation\"."));
        }

        return errors.isEmpty()
                ? ParseResult.success(new ParsedPlan(header, phases))
                : ParseResult.failure(errors);
    }

    // ---------------------------------------------------------------- header

    private PlanHeader parseHeader(String[] lines, int from, int to, int openingLine, List<ParseError> errors) {
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

        String category = values.get("category").toLowerCase();
        if (!SUPPORTED_CATEGORIES.contains(category)) {
            errors.add(new ParseError(lineOf.get("category"),
                    "Category \"" + values.get("category") + "\" isn't supported yet. Use \"category: workout\"."));
        }

        Integer weeks = parsePositiveInt(values.get("weeks"));
        if (weeks == null || weeks > MAX_WEEKS) {
            errors.add(new ParseError(lineOf.get("weeks"),
                    "\"weeks\" must be a whole number from 1 to " + MAX_WEEKS + "."));
        }

        if (errors.size() > errorsBefore) {
            return null;
        }
        return new PlanHeader(version, values.get("title"), category, weeks);
    }

    // ------------------------------------------------------------------ body

    /**
     * Walks the plan below the header, building phases, days and exercises.
     *
     * <p>When a heading is invalid, the lines under it are skipped quietly,
     * so one mistake doesn't produce a cascade of follow-on errors.
     */
    private List<ParsedPhase> parseBody(String[] lines, int from, PlanHeader header, List<ParseError> errors) {
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
                day = parseDayHeading(line, lineNo, errors);
                if (day == null) {
                    inBrokenDay = true;
                } else {
                    phase.days.add(day);
                }

            } else if (line.startsWith("##")) {
                closePhase(phase, phases, errors);
                phase = parsePhaseHeading(line, lineNo, header, errors);
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
            }
            // Any other text (blank lines, a stray sentence from the AI) is ignored.
        }
        closePhase(phase, phases, errors);
        return phases;
    }

    private PhaseBuilder parsePhaseHeading(String line, int lineNo, PlanHeader header, List<ParseError> errors) {
        Matcher m = PHASE_HEADING.matcher(line);
        if (!m.matches()) {
            errors.add(new ParseError(lineNo,
                    "Phase headings must look like \"## Weeks 1-4: Name\" or \"## Week 5: Name\"."));
            return null;
        }

        int fromWeek = Integer.parseInt(m.group(1));
        int toWeek = m.group(2) != null ? Integer.parseInt(m.group(2)) : fromWeek;
        String name = m.group(3) != null ? m.group(3).trim() : "";

        if (fromWeek < 1) {
            errors.add(new ParseError(lineNo, "Weeks start at 1, not " + fromWeek + "."));
            return null;
        }
        if (fromWeek > toWeek) {
            errors.add(new ParseError(lineNo,
                    "Week range " + fromWeek + "-" + toWeek + " is backwards. Write it as " + toWeek + "-" + fromWeek + "."));
            return null;
        }
        if (header != null && toWeek > header.weeks()) {
            errors.add(new ParseError(lineNo,
                    "Week " + toWeek + " is past the end of the plan (weeks: " + header.weeks() + ")."));
            return null;
        }
        return new PhaseBuilder(fromWeek, toWeek, name, lineNo);
    }

    private DayBuilder parseDayHeading(String line, int lineNo, List<ParseError> errors) {
        Matcher m = DAY_HEADING.matcher(line);
        if (!m.matches()) {
            errors.add(new ParseError(lineNo,
                    "Day headings must look like \"### Mon: Push\" (Mon, Tue, Wed, Thu, Fri, Sat or Sun)."));
            return null;
        }
        DayOfWeek weekday = WEEKDAYS.get(m.group(1).substring(0, 3).toLowerCase());
        String title = m.group(2) != null ? m.group(2).trim() : "";
        return new DayBuilder(weekday, title, lineNo);
    }

    /** Parses "Name | sets x reps | rest 60s | note: text" (the bullet already removed). */
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

        Matcher sr = SETS_REPS.matcher(fields[1].trim());
        if (!sr.matches()) {
            errors.add(new ParseError(lineNo,
                    "\"" + fields[1].trim() + "\" isn't sets x reps. Write it like 3x10, 3x30s or 1xmax."));
            return null;
        }
        int sets = Integer.parseInt(sr.group(1));
        if (sets < 1 || sets > MAX_SETS) {
            errors.add(new ParseError(lineNo, "Sets must be from 1 to " + MAX_SETS + ", not " + sets + "."));
            return null;
        }
        // "3x10 reps" and "3x10" are the same plan; the word adds nothing to track.
        Reps reps = parseReps(TRAILING_REPS.matcher(sr.group(2).trim()).replaceFirst("").trim());

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

    private void closePhase(PhaseBuilder phase, List<ParsedPhase> phases, List<ParseError> errors) {
        if (phase == null) {
            return;
        }
        List<ParsedDay> days = new ArrayList<>();
        for (DayBuilder d : phase.days) {
            if (d.items.isEmpty()) {
                errors.add(new ParseError(d.line,
                        "This day has no exercises. Add lines like \"- Push-ups | 3x10\", or remove the day to make it a rest day."));
            }
            days.add(new ParsedDay(d.weekday, d.title, d.line, d.items));
        }
        if (days.isEmpty()) {
            errors.add(new ParseError(phase.line,
                    "This phase has no training days. Add a day like \"### Mon: Push\" under it."));
        }
        phases.add(new ParsedPhase(phase.fromWeek, phase.toWeek, phase.name, phase.line, days));
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
        final int fromWeek;
        final int toWeek;
        final String name;
        final int line;
        final List<DayBuilder> days = new ArrayList<>();

        PhaseBuilder(int fromWeek, int toWeek, String name, int line) {
            this.fromWeek = fromWeek;
            this.toWeek = toWeek;
            this.name = name;
            this.line = line;
        }
    }

    private static final class DayBuilder {
        final DayOfWeek weekday;
        final String title;
        final int line;
        final List<ParsedItem> items = new ArrayList<>();

        DayBuilder(DayOfWeek weekday, String title, int line) {
            this.weekday = weekday;
            this.title = title;
            this.line = line;
        }
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
