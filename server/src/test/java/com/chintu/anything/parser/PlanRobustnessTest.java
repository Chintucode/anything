package com.chintu.anything.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What the parser does with input no sane person would write — because an AI will.
 *
 * <p>The promise in {@link PlanParser}'s own documentation is that it never throws on bad
 * input: every problem comes back as a line number and a sentence. Numbers long enough to
 * overflow an int used to break that promise, and the person pasting the plan was told
 * "something went wrong on our side" for a fault in their own text.
 */
class PlanRobustnessTest {

    private final PlanParser parser = new PlanParser();

    private static String plan(String item) {
        return """
                ---
                anything: 1
                title: T
                category: workout
                weeks: 8
                ---

                ## Weeks 1-8: Foundation

                ### Mon: Push
                """ + item + "\n";
    }

    private List<String> messages(ParseResult result) {
        return result.errors().stream().map(ParseError::message).toList();
    }

    @Nested
    @DisplayName("a number too big for the machine")
    class AbsurdNumbers {

        @Test
        void neverThrows() {
            for (String item : List.of(
                    "- Push-ups | 99999999999x10",
                    "- Push-ups | 3x99999999999",
                    "- Push-ups | 3x10 | rest 99999999999s",
                    "- Push-ups | 3x10 | rest 100000000m",
                    "- Push-ups | 9999999999999999999999x10")) {
                assertThatCode(() -> parser.parse(plan(item))).doesNotThrowAnyException();
            }
        }

        @Test
        void comesBackAsSomethingTheReaderCanFix() {
            ParseResult result = parser.parse(plan("- Push-ups | 3x99999999999"));

            assertThat(result.isOk()).isFalse();
            assertThat(messages(result).get(0)).contains("isn't sets x reps or a length");
            assertThat(result.errors().get(0).line()).isEqualTo(11);
        }

        @Test
        void aHugePhaseRangeOrDayNumberIsAlsoJustAnError() {
            assertThatCode(() -> parser.parse("""
                    ---
                    anything: 1
                    title: T
                    category: workout
                    weeks: 8
                    ---

                    ## Weeks 99999999999-99999999999: Foundation

                    ### Mon: Push
                    - Push-ups | 3x8
                    """)).doesNotThrowAnyException();

            assertThatCode(() -> parser.parse("""
                    ---
                    anything: 1
                    title: T
                    category: meditation
                    days: 3
                    ---

                    ## Days 1-3: Start

                    ### Day 99999999999: Breathe
                    - Breath | 5m
                    """)).doesNotThrowAnyException();
        }

        @Test
        void aRestLongerThanADayIsRejectedRatherThanStoredAsNonsense() {
            ParseResult result = parser.parse(plan("- Push-ups | 3x10 | rest 9999999m"));

            assertThat(result.isOk()).isFalse();
            assertThat(messages(result).get(0)).contains("longer than a whole day");
        }

        @Test
        void ordinaryRestsAreUntouched() {
            ParseResult ok = parser.parse(plan("- Push-ups | 3x10 | rest 2m"));
            assertThat(ok.isOk()).isTrue();
            assertThat(ok.plan().phases().get(0).days().get(0).items().get(0).restSeconds()).isEqualTo(120);
        }
    }

    @Nested
    @DisplayName("a course that stops halfway")
    class HalfWrittenCourses {

        private static final String TWO_OF_TWENTY_ONE = """
                ---
                anything: 1
                title: 21-Day Focus Course
                category: meditation
                days: 21
                ---

                ## Days 1-21: Showing Up

                ### Day 1: Just Breathe
                - Breath awareness | 5m

                ### Day 2: Body Scan
                - Body scan | 5m
                """;

        @Test
        void isRefusedWhileTheTextIsStillOnScreen() {
            ParseResult result = parser.parse(TWO_OF_TWENTY_ONE);

            assertThat(result.isOk()).isFalse();
            assertThat(messages(result)).anySatisfy(m -> assertThat(m)
                    .contains("Days 3-21 are missing")
                    .contains("continue the pattern"));
        }

        @Test
        void aGapInTheMiddleIsNamedExactly() {
            ParseResult result = parser.parse("""
                    ---
                    anything: 1
                    title: C
                    category: study
                    days: 5
                    ---

                    ## Days 1-5: All of it

                    ### Day 1: A
                    - Read | 10m

                    ### Day 2: B
                    - Read | 10m

                    ### Day 5: E
                    - Read | 10m
                    """);

            assertThat(messages(result)).anySatisfy(m -> assertThat(m).contains("Days 3-4 are missing"));
        }

        @Test
        void oneMissingDayIsSingular() {
            ParseResult result = parser.parse("""
                    ---
                    anything: 1
                    title: C
                    category: study
                    days: 2
                    ---

                    ## Days 1-2: Both

                    ### Day 1: A
                    - Read | 10m
                    """);

            assertThat(messages(result)).anySatisfy(m -> assertThat(m).contains("Day 2 is missing"));
        }

        @Test
        void oneErrorPerGapRatherThanOnePerDay() {
            ParseResult result = parser.parse(TWO_OF_TWENTY_ONE);

            long missing = messages(result).stream().filter(m -> m.contains("missing")).count();
            assertThat(missing).isEqualTo(1);
        }

        @Test
        void aCompleteCourseStillPasses() throws IOException {
            ParseResult result = parser.parse(Files.readString(Path.of("../fixtures/meditation.md")));

            assertThat(result.isOk()).isTrue();
            assertThat(result.plan().header().length()).isEqualTo(21);
        }

        @Test
        void aWeeklyPlanIsNotHeldToThisAtAll() {
            // A week with nothing in it is a rest week, which is a real thing to want.
            ParseResult result = parser.parse(plan("- Push-ups | 3x8"));

            assertThat(result.isOk()).isTrue();
        }
    }
}
