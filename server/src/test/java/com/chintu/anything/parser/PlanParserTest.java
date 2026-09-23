package com.chintu.anything.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PlanParserTest {

    private final PlanParser parser = new PlanParser();

    private static final String HEADER = """
            ---
            anything: 1
            title: Test Plan
            category: workout
            weeks: 8
            ---
            """;

    /** A minimal valid training day, used wherever a test only cares about something else. */
    private static final String DAY = "### Mon: Push\n- Push-ups | 3x10\n";

    /** A full valid body covering all 8 weeks. Header is lines 1-6, so this starts at line 7. */
    private static final String BODY = "## Weeks 1-8: All\n" + DAY;

    private static String fixture(String name) throws IOException {
        // Tests run from server/, so the fixtures folder is one level up.
        return Files.readString(Path.of("../fixtures/" + name));
    }

    // ------------------------------------------------------------ happy path

    @Nested
    class CalisthenicsFixture {

        private final ParsedPlan plan = parseFixture();

        private ParsedPlan parseFixture() {
            try {
                ParseResult result = parser.parse(fixture("calisthenics.md"));
                assertThat(result.errors()).isEmpty();
                return result.plan();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        void readsTheHeader() {
            PlanHeader header = plan.header();
            assertThat(header.formatVersion()).isEqualTo(1);
            assertThat(header.title()).isEqualTo("12-Week Calisthenics Strength");
            assertThat(header.category()).isEqualTo("workout");
            assertThat(header.weeks()).isEqualTo(12);
        }

        @Test
        void readsThreePhasesOfFourDays() {
            assertThat(plan.phases())
                    .extracting(ParsedPhase::from, ParsedPhase::to, ParsedPhase::name)
                    .containsExactly(tuple(1, 4, "Foundation"), tuple(5, 8, "Build"), tuple(9, 12, "Strength"));

            assertThat(plan.phases().get(0).days())
                    .extracting(ParsedDay::weekday, ParsedDay::title)
                    .containsExactly(
                            tuple(DayOfWeek.MONDAY, "Push"),
                            tuple(DayOfWeek.TUESDAY, "Pull"),
                            tuple(DayOfWeek.THURSDAY, "Legs + Core"),
                            tuple(DayOfWeek.SATURDAY, "Full Body"));
        }

        @Test
        void readsAll48Exercises() {
            long total = plan.phases().stream()
                    .flatMap(p -> p.days().stream())
                    .mapToLong(d -> d.items().size())
                    .sum();
            assertThat(total).isEqualTo(48);
        }

        @Test
        void readsAFullExerciseLine() {
            ParsedItem pushUps = plan.phases().get(0).days().get(0).items().get(0);

            assertThat(pushUps.name()).isEqualTo("Push-ups");
            assertThat(pushUps.sets()).isEqualTo(4);
            assertThat(pushUps.reps().kind()).isEqualTo(Reps.Kind.COUNT);
            assertThat(pushUps.reps().value()).isEqualTo(10);
            assertThat(pushUps.restSeconds()).isEqualTo(90);
            assertThat(pushUps.note()).isEqualTo("elbows at 45 degrees, full lockout");
        }

        @Test
        void readsTimedPerSideAndMaxReps() {
            List<ParsedItem> push = plan.phases().get(0).days().get(0).items();
            List<ParsedItem> legs = plan.phases().get(0).days().get(2).items();
            List<ParsedItem> testDay = plan.phases().get(2).days().get(3).items();

            Reps plank = push.get(3).reps();
            assertThat(plank.kind()).isEqualTo(Reps.Kind.SECONDS);
            assertThat(plank.value()).isEqualTo(40);

            Reps lunges = legs.get(1).reps();
            assertThat(lunges.kind()).isEqualTo(Reps.Kind.COUNT);
            assertThat(lunges.value()).isEqualTo(10);
            assertThat(lunges.detail()).isEqualTo("each leg");

            ParsedItem maxPushUps = testDay.get(0);
            assertThat(maxPushUps.reps().kind()).isEqualTo(Reps.Kind.MAX);
            assertThat(maxPushUps.restSeconds()).isNull();
        }
    }

    @Test
    void acceptsASingleWeekPhaseWithoutAName() {
        ParseResult result = parser.parse(HEADER + "## Weeks 1-7: Base\n" + DAY + "## Week 8\n" + DAY);

        assertThat(result.errors()).isEmpty();
        ParsedPhase last = result.plan().phases().get(1);
        assertThat(last.from()).isEqualTo(8);
        assertThat(last.to()).isEqualTo(8);
        assertThat(last.name()).isEmpty();
    }

    @Test
    void acceptsALongerRowOfDashesAroundTheHeader() {
        // AI models often close the header with "-------" instead of "---".
        ParseResult result = parser.parse(HEADER.replace("---\n", "-------\n") + BODY);

        assertThat(result.errors()).isEmpty();
        assertThat(result.plan().header().title()).isEqualTo("Test Plan");
    }

    @Test
    void handlesWindowsLineEndings() {
        assertThat(parser.parse((HEADER + BODY).replace("\n", "\r\n")).errors()).isEmpty();
    }

    @Test
    void ignoresLeadingBlankLinesStrayTextAndUnknownHeaderKeys() {
        String plan = "\n\n" + HEADER.replace("weeks: 8", "weeks: 8\nauthor: Claude")
                + "Here is your plan!\n" + BODY + "\nGood luck!\n";

        assertThat(parser.parse(plan).errors()).isEmpty();
    }

    @Test
    void acceptsFullWeekdayNamesAndDaysWithoutATitle() {
        ParseResult result = parser.parse(HEADER + "## Weeks 1-8: All\n"
                + "### Monday: Push\n- Push-ups | 3x10\n"
                + "### fri\n- Squats | 3x15\n");

        assertThat(result.errors()).isEmpty();
        assertThat(result.plan().phases().get(0).days())
                .extracting(ParsedDay::weekday, ParsedDay::title)
                .containsExactly(tuple(DayOfWeek.MONDAY, "Push"), tuple(DayOfWeek.FRIDAY, ""));
    }

    @Test
    void acceptsOtherBulletStylesAndCheckboxes() {
        ParseResult result = parser.parse(HEADER + "## Weeks 1-8: All\n### Mon: Push\n"
                + "* Push-ups | 3x10\n"
                + "1. Dips | 3x8\n"
                + "- [ ] Plank | 3x30s\n"
                + "- [x] Pike push-ups | 3x6\n");

        assertThat(result.errors()).isEmpty();
        assertThat(result.plan().phases().get(0).days().get(0).items())
                .extracting(ParsedItem::name)
                .containsExactly("Push-ups", "Dips", "Plank", "Pike push-ups");
    }

    @Test
    void readsEveryRepsAndRestStyle() {
        ParseResult result = parser.parse(HEADER + "## Weeks 1-8: All\n### Mon: Mixed\n"
                + "- A | 3x30s | rest 2m\n"
                + "- B | 3 x 2 min | rest 90\n"
                + "- C | 4×8 each leg\n"
                + "- D | 3x45 sec each side\n"
                + "- E | 1xMAX\n"
                + "- F | 2xAMRAP in 5 minutes\n");

        assertThat(result.errors()).isEmpty();
        assertThat(result.plan().phases().get(0).days().get(0).items())
                .extracting(i -> i.reps().kind(), i -> i.reps().value(), i -> i.reps().detail(), ParsedItem::restSeconds)
                .containsExactly(
                        tuple(Reps.Kind.SECONDS, 30, "", 120),
                        tuple(Reps.Kind.SECONDS, 120, "", 90),
                        tuple(Reps.Kind.COUNT, 8, "each leg", null),
                        tuple(Reps.Kind.SECONDS, 45, "each side", null),
                        tuple(Reps.Kind.MAX, null, "", null),
                        tuple(Reps.Kind.TEXT, null, "", null));
    }

    // --------------------------------------------------------- header errors

    @Nested
    class HeaderErrors {

        @Test
        void emptyInput() {
            ParseResult result = parser.parse("   ");

            assertThat(result.isOk()).isFalse();
            assertThat(result.plan()).isNull();
            assertThat(result.errors()).singleElement()
                    .satisfies(e -> assertThat(e.message()).contains("empty"));
        }

        @Test
        void missingHeader() {
            ParseResult result = parser.parse(BODY);

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> {
                        assertThat(e.line()).isEqualTo(1);
                        assertThat(e.message()).contains("header block");
                    });
        }

        @Test
        void headerNeverClosed() {
            ParseResult result = parser.parse("---\nanything: 1\ntitle: X\n");

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> {
                        assertThat(e.line()).isEqualTo(1);
                        assertThat(e.message()).contains("never closed");
                    });
        }

        @Test
        void missingTitle() {
            ParseResult result = parser.parse(HEADER.replace("title: Test Plan\n", "") + BODY);

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> assertThat(e.message()).contains("\"title\""));
        }

        @Test
        void weeksIsNotANumber() {
            ParseResult result = parser.parse(HEADER.replace("weeks: 8", "weeks: twelve") + BODY);

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> {
                        assertThat(e.line()).isEqualTo(5);
                        assertThat(e.message()).contains("whole number");
                    });
        }

        @Test
        void unsupportedFormatVersion() {
            ParseResult result = parser.parse(HEADER.replace("anything: 1", "anything: 2") + BODY);

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> {
                        assertThat(e.line()).isEqualTo(2);
                        assertThat(e.message()).contains("anything: 1");
                    });
        }

        @Test
        void anyShortCategoryIsAccepted() {
            ParseResult result = parser.parse(HEADER.replace("category: workout", "category: Study Plan") + BODY);

            assertThat(result.errors()).isEmpty();
            assertThat(result.plan().header().category()).isEqualTo("study plan");
        }

        @Test
        void aCategoryHasToBeAWordOrTwo() {
            ParseResult result = parser.parse(HEADER.replace("category: workout", "category: 🧘 zen!!") + BODY);

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> assertThat(e.message()).contains("short word or two"));
        }

        @Test
        void missingWeeks() {
            ParseResult result = parser.parse(HEADER.replace("weeks: 8\n", "") + BODY);

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> assertThat(e.message()).contains("missing \"weeks\""));
        }

        @Test
        void duplicateKey() {
            ParseResult result = parser.parse(HEADER.replace("weeks: 8", "weeks: 8\ntitle: Again") + BODY);

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> {
                        assertThat(e.line()).isEqualTo(6);
                        assertThat(e.message()).contains("appears twice");
                    });
        }
    }

    // ---------------------------------------------------------- phase errors

    @Nested
    class PhaseErrors {

        @Test
        void backwardsRange() {
            ParseResult result = parser.parse(HEADER + "## Weeks 5-3: Oops\n" + DAY);

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> {
                        assertThat(e.line()).isEqualTo(7);
                        assertThat(e.message()).contains("backwards");
                    });
        }

        @Test
        void weekZero() {
            ParseResult result = parser.parse(HEADER + "## Weeks 0-4: Oops\n" + DAY);

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> assertThat(e.message()).contains("start at 1"));
        }

        @Test
        void rangePastTheEndOfThePlan() {
            ParseResult result = parser.parse(HEADER + "## Weeks 1-10: Too long\n" + DAY);

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> assertThat(e.message()).contains("past the end"));
        }

        @Test
        void malformedHeading() {
            ParseResult result = parser.parse(HEADER + "## Phase one\n" + DAY);

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> {
                        assertThat(e.line()).isEqualTo(7);
                        assertThat(e.message()).contains("## Weeks 1-4: Name");
                    });
        }

        @Test
        void noPhasesAtAll() {
            ParseResult result = parser.parse(HEADER + "Just some text\n");

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> assertThat(e.message()).contains("No phases found"));
        }

        @Test
        void phaseWithNoDays() {
            ParseResult result = parser.parse(HEADER + "## Weeks 1-4: A\n## Weeks 5-8: B\n" + DAY);

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> {
                        assertThat(e.line()).isEqualTo(7);
                        assertThat(e.message()).contains("no training days");
                    });
        }

        @Test
        void lineTooLong() {
            ParseResult result = parser.parse(HEADER + BODY + "- " + "a".repeat(600) + " | 3x10\n");

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> {
                        assertThat(e.line()).isEqualTo(10);
                        assertThat(e.message()).contains("too long");
                    });
        }

        @Test
        void collectsEveryErrorInOnePass() {
            ParseResult result = parser.parse(HEADER + "## Weeks 5-3: A\n## Phase B\n## Weeks 1-99: C\n");

            assertThat(result.errors())
                    .extracting(ParseError::line)
                    .containsExactly(7, 8, 9);
        }
    }

    // ------------------------------------------------- day and item errors

    @Nested
    class DayAndExerciseErrors {

        @Test
        void dayBeforeAnyPhase() {
            ParseResult result = parser.parse(HEADER + DAY + BODY);

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> {
                        assertThat(e.line()).isEqualTo(7);
                        assertThat(e.message()).contains("after a phase heading");
                    });
        }

        @Test
        void unknownWeekday() {
            ParseResult result = parser.parse(HEADER + BODY + "### Funday: Rest\n- Walk | 1x20m\n");

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> {
                        assertThat(e.line()).isEqualTo(10);
                        assertThat(e.message()).contains("### Mon: Push");
                    });
        }

        @Test
        void dayWithNoExercises() {
            ParseResult result = parser.parse(HEADER + BODY + "### Wed: Pull\n");

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> {
                        assertThat(e.line()).isEqualTo(10);
                        assertThat(e.message()).contains("no exercises");
                    });
        }

        @Test
        void missingSetsAndReps() {
            ParseResult result = parser.parse(HEADER + BODY + "- Dips\n");

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> {
                        assertThat(e.line()).isEqualTo(10);
                        assertThat(e.message()).contains("needs sets and reps");
                    });
        }

        @Test
        void missingName() {
            ParseResult result = parser.parse(HEADER + BODY + "- | 3x10\n");

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> assertThat(e.message()).contains("no name"));
        }

        @Test
        void tooManySets() {
            ParseResult result = parser.parse(HEADER + BODY + "- Dips | 50x10\n");

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> assertThat(e.message()).contains("from 1 to 20"));
        }

        @Test
        void invalidPhaseDoesNotCascade() {
            // One bad heading: its days and exercises are skipped quietly, not reported again.
            ParseResult result = parser.parse(HEADER + BODY
                    + "## Phase two\n### Tue: Pull\n- Rows | ten\n- Pull-ups\n");

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> assertThat(e.line()).isEqualTo(10));
        }
    }

    // ------------------------------------------------- whole-plan checks

    @Nested
    class WholePlanChecks {

        @Test
        void gapBetweenPhases() {
            ParseResult result = parser.parse(HEADER + "## Weeks 1-3: A\n" + DAY + "## Weeks 6-8: B\n" + DAY);

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> {
                        assertThat(e.line()).isEqualTo(10);
                        assertThat(e.message()).contains("Weeks 4-5 are not covered");
                    });
        }

        @Test
        void missingWeeksAtTheEnd() {
            ParseResult result = parser.parse(HEADER + "## Weeks 1-6: A\n" + DAY);

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> {
                        assertThat(e.line()).isEqualTo(7);
                        assertThat(e.message()).contains("Weeks 7-8 are not covered");
                    });
        }

        @Test
        void missingWeekAtTheStart() {
            ParseResult result = parser.parse(HEADER + "## Weeks 2-8: A\n" + DAY);

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> assertThat(e.message()).contains("Week 1 is not covered"));
        }

        @Test
        void overlappingPhases() {
            ParseResult result = parser.parse(HEADER + "## Weeks 1-5: A\n" + DAY + "## Weeks 4-8: B\n" + DAY);

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> {
                        assertThat(e.line()).isEqualTo(10);
                        assertThat(e.message()).contains("overlap with the phase on line 7");
                    });
        }

        @Test
        void phasesOutOfOrderAreAcceptedAndSorted() {
            ParseResult result = parser.parse(HEADER + "## Weeks 5-8: Later\n" + DAY + "## Weeks 1-4: First\n" + DAY);

            assertThat(result.errors()).isEmpty();
            assertThat(result.plan().phases())
                    .extracting(ParsedPhase::name)
                    .containsExactly("First", "Later");
        }

        @Test
        void sameWeekdayTwiceInAPhase() {
            ParseResult result = parser.parse(HEADER + BODY + "### Monday: More push\n- Dips | 3x8\n");

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> {
                        assertThat(e.line()).isEqualTo(10);
                        assertThat(e.message()).contains("Monday appears twice in this phase (first on line 8)");
                    });
        }

        @Test
        void sameWeekdayInDifferentPhasesIsFine() {
            ParseResult result = parser.parse(HEADER + "## Weeks 1-4: A\n" + DAY + "## Weeks 5-8: B\n" + DAY);

            assertThat(result.errors()).isEmpty();
        }
    }

    // --------------------------------------------------- broken fixtures

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = ';', value = {
            "broken/01-missing-header.md;      1;  header block",
            "broken/02-backwards-weeks.md;     13; backwards",
            "broken/03-exercise-before-day.md; 9;  after a day heading",
            "broken/04-bad-sets-reps.md;       12; isn't sets x reps",
            "broken/05-unknown-field.md;       12; Unknown field \"tempo 3-1-1\"",
    })
    void brokenFixturesFailWithOneClearError(String file, int line, String message) throws IOException {
        ParseResult result = parser.parse(fixture(file));

        assertThat(result.plan()).isNull();
        assertThat(result.errors()).singleElement()
                .satisfies(e -> {
                    assertThat(e.line()).isEqualTo(line);
                    assertThat(e.message()).contains(message);
                });
    }

    // ------------------------------------------- what models write by reflex

    /**
     * The prompt asks for a clean plan. These are the things a model adds anyway,
     * and none of them is worth making a person fix by hand.
     */
    @Nested
    class AiTidiness {

        @Test
        void ignoresACodeFenceWrappedAroundThePlan() {
            ParseResult result = parser.parse("```markdown\n" + HEADER + BODY + "```\n");

            assertThat(result.errors()).isEmpty();
            assertThat(result.plan().phases()).hasSize(1);
        }

        @Test
        void stripsBoldFromHeadingsAndNames() {
            ParseResult result = parser.parse(HEADER
                    + "## **Weeks 1-8: All**\n"
                    + "### **Mon: Push**\n"
                    + "- **Push-ups** | 3x10\n");

            assertThat(result.errors()).isEmpty();
            ParsedDay day = result.plan().phases().get(0).days().get(0);
            assertThat(day.title()).isEqualTo("Push");
            assertThat(day.items().get(0).name()).isEqualTo("Push-ups");
        }

        @Test
        void acceptsSetsAndRepsWrittenOutInWords() {
            ParseResult result = parser.parse(HEADER
                    + "## Weeks 1-8: All\n### Mon: Push\n- Push-ups | 3 sets x 10 reps\n");

            assertThat(result.errors()).isEmpty();
            ParsedItem item = result.plan().phases().get(0).days().get(0).items().get(0);
            assertThat(item.sets()).isEqualTo(3);
            assertThat(item.reps().kind()).isEqualTo(Reps.Kind.COUNT);
            assertThat(item.reps().value()).isEqualTo(10);
            assertThat(item.reps().detail()).isEmpty();
        }

        @Test
        void acceptsThreeSetsOfTen() {
            ParseResult result = parser.parse(HEADER
                    + "## Weeks 1-8: All\n### Mon: Push\n- Plank | 3 sets of 30s\n");

            assertThat(result.errors()).isEmpty();
            ParsedItem item = result.plan().phases().get(0).days().get(0).items().get(0);
            assertThat(item.sets()).isEqualTo(3);
            assertThat(item.reps().kind()).isEqualTo(Reps.Kind.SECONDS);
            assertThat(item.reps().value()).isEqualTo(30);
        }

        @Test
        void acceptsFullWeekdayNames() {
            ParseResult result = parser.parse(HEADER
                    + "## Weeks 1-8: All\n### Wednesday: Legs\n- Squats | 3x15\n");

            assertThat(result.errors()).isEmpty();
            assertThat(result.plan().phases().get(0).days().get(0).weekday()).isEqualTo(DayOfWeek.WEDNESDAY);
        }

        @Test
        void aLineNumberStillPointsAtTheRightLineAfterTidying() {
            ParseResult result = parser.parse("```\n" + HEADER
                    + "## Weeks 1-8: All\n### Mon: Push\n- Push-ups | three by ten\n```\n");

            // Fence on line 1, header lines 2-7, phase 8, day 9, the bad exercise on 10.
            assertThat(result.errors().get(0).line()).isEqualTo(10);
            assertThat(result.errors().get(0).message()).contains("isn't sets x reps");
        }
    }

    // ------------------------------------------- day-by-day courses

    /**
     * A second shape of plan: "Day 1, Day 2, Day 3", worked through in order rather
     * than pinned to weekdays. Three different AI models, asked for a 21-day
     * meditation plan with no format given, all wrote it this way.
     */
    @Nested
    class DayByDayCourses {

        private static final String COURSE = """
                ---
                anything: 1
                title: Course
                category: meditation
                days: 21
                ---
                """;

        @Test
        void theMeditationFixtureParses() throws IOException {
            ParseResult result = parser.parse(fixture("meditation.md"));

            assertThat(result.errors()).isEmpty();
            PlanHeader header = result.plan().header();
            assertThat(header.schedule()).isEqualTo(PlanHeader.Schedule.SEQUENTIAL);
            assertThat(header.category()).isEqualTo("meditation");
            assertThat(header.length()).isEqualTo(21);
            assertThat(header.weeks()).isEqualTo(3);
            assertThat(result.plan().phases())
                    .extracting(ParsedPhase::from, ParsedPhase::to)
                    .containsExactly(tuple(1, 7), tuple(8, 14), tuple(15, 21));
            assertThat(result.plan().phases().stream().mapToInt(ph -> ph.days().size()).sum()).isEqualTo(21);
        }

        @Test
        void aDayHasANumberAndNoWeekday() throws IOException {
            ParsedDay day1 = parser.parse(fixture("meditation.md")).plan().phases().get(0).days().get(0);

            assertThat(day1.dayNumber()).isEqualTo(1);
            assertThat(day1.weekday()).isNull();
            assertThat(day1.title()).isEqualTo("Just Breathe");
        }

        @Test
        void theParagraphUnderADayIsItsDescription() throws IOException {
            ParsedDay day1 = parser.parse(fixture("meditation.md")).plan().phases().get(0).days().get(0);

            assertThat(day1.description()).startsWith("Sit comfortably and close your eyes.")
                    .endsWith("When the mind wanders, gently come back.");
        }

        @Test
        void theLineUnderAPhaseIsItsDescription() throws IOException {
            ParsedPhase first = parser.parse(fixture("meditation.md")).plan().phases().get(0);

            assertThat(first.description())
                    .isEqualTo("Build the habit and learn to notice what's happening in your body and mind.");
        }

        @Test
        void aBareDurationIsOneBlockOfIt() {
            ParseResult result = parser.parse(COURSE + "## Days 1-21: All\n### Day 1: Sit\n- Breath awareness | 10m\n");

            ParsedItem item = result.plan().phases().get(0).days().get(0).items().get(0);
            assertThat(item.sets()).isEqualTo(1);
            assertThat(item.reps().kind()).isEqualTo(Reps.Kind.SECONDS);
            assertThat(item.reps().value()).isEqualTo(600);
        }

        @Test
        void weekChaptersInACourseMeanSevenDaysEach() {
            ParseResult result = parser.parse(COURSE
                    + "## Week 1 — Showing Up\n### Day 1 — Just Breathe\n- Breath | 5m\n"
                    + "## Week 2 — Deepening\n### Day 8: Longer\n- Breath | 10m\n"
                    + "## Week 3 — Integrating\n### Day 21: Close\n- Open awareness | 15 min\n");

            assertThat(result.errors()).isEmpty();
            assertThat(result.plan().phases())
                    .extracting(ParsedPhase::from, ParsedPhase::to, ParsedPhase::name)
                    .containsExactly(tuple(1, 7, "Showing Up"), tuple(8, 14, "Deepening"), tuple(15, 21, "Integrating"));
            assertThat(result.plan().phases().get(0).days().get(0).title()).isEqualTo("Just Breathe");
        }

        @Test
        void mixingWeekdaysAndDayNumbersIsRefused() {
            ParseResult result = parser.parse(COURSE
                    + "## Days 1-21: All\n### Day 1: A\n- X | 5m\n### Mon: B\n- Y | 3x10\n");

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> assertThat(e.message()).contains("mixes weekdays"));
        }

        @Test
        void aCourseNeedsDaysNotWeeksInTheHeader() {
            ParseResult result = parser.parse(COURSE.replace("days: 21", "weeks: 3")
                    + "## Days 1-21: All\n### Day 1: A\n- X | 5m\n");

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> assertThat(e.message()).contains("needs \"days: <total>\""));
        }

        @Test
        void thereIsOnlyOneDayNine() {
            ParseResult result = parser.parse(COURSE
                    + "## Days 1-21: All\n### Day 9: A\n- X | 5m\n### Day 9: B\n- Y | 5m\n");

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> assertThat(e.message()).contains("Day 9 appears twice"));
        }

        @Test
        void aDaySitsInsideItsPhase() {
            ParseResult result = parser.parse(COURSE
                    + "## Days 1-7: A\n### Day 9: Wrong\n- X | 5m\n## Days 8-21: B\n### Day 10: B\n- Y | 5m\n");

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> assertThat(e.message()).contains("isn't inside this phase"));
        }

        @Test
        void everyDayIsCoveredByAPhase() {
            ParseResult result = parser.parse(COURSE
                    + "## Days 1-7: A\n### Day 1: A\n- X | 5m\n## Days 15-21: C\n### Day 15: C\n- Y | 5m\n");

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> assertThat(e.message()).contains("Days 8-14 are not covered"));
        }

        @Test
        void aWeekdayPlanStillThinksInWeeks() {
            ParseResult result = parser.parse(HEADER + "## Days 1-8: All\n" + DAY);

            assertThat(result.errors()).singleElement()
                    .satisfies(e -> assertThat(e.message()).contains("its phases are weeks"));
        }

        @Test
        void aWorkoutDayCanHaveADescriptionToo() {
            ParseResult result = parser.parse(HEADER
                    + "## Weeks 1-8: All\n### Mon: Push\nWarm up for five minutes first.\n- Push-ups | 3x10\n");

            assertThat(result.plan().phases().get(0).days().get(0).description())
                    .isEqualTo("Warm up for five minutes first.");
        }
    }
}
