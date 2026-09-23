/**
 * The prompt users paste into their AI before asking for a plan.
 *
 * The first version described the format and hoped. It lost on the things
 * models do by reflex: a friendly sentence before the plan, a code fence around
 * it, **bold** on every name, "Monday" where the parser wants "Mon",
 * "3 sets x 10 reps" where it wants "3x10", and a tips section at the end.
 *
 * What changed: complete worked examples (a model copies a shape far more
 * reliably than it follows a description), the failure modes named out loud as
 * bans, and a checklist to run before answering — every line of which maps to a
 * real parser error.
 *
 * Two shapes, because plans come in two kinds. Asked for a 21-day meditation
 * course with no format given, DeepSeek, ChatGPT and Gemini all wrote "Day 1,
 * Day 2, Day 3" with no weekdays anywhere. A training program is pinned to
 * weekdays; a course is worked through in order. The prompt lets the model pick.
 *
 * Keep it in sync with the parser rules on the server.
 */
export const ANYTHING_PROMPT = `You are writing a plan in the "Anything" format. An app reads it line by line and turns it into a daily checklist: every day heading becomes one screen, every item line becomes one tick box. A line it can't read is an error the person has to fix by hand, so the format matters more than the prose.

Output the plan and nothing else. No sentence before it, no summary after it, no code block around it, and no bold, italic or emoji anywhere in it.

FIRST, PICK THE SHAPE

- A WEEKLY plan repeats on set weekdays — a gym or training program. Days are "### Mon:", "### Thu:". Its length is in weeks.
- A DAY-BY-DAY plan is worked through in order — a meditation course, a 30-day challenge, a reading plan. Days are "### Day 1:", "### Day 2:". Its length is in days.

Use one shape for the whole plan. Never mix weekdays and day numbers.

THE WEEKLY SHAPE

---
anything: 1
title: 8-Week Beginner Strength
category: workout
weeks: 8
---

## Weeks 1-4: Foundation

### Mon: Push
- Push-ups | 3x8 | rest 90s | note: elbows tucked at 45 degrees
- Plank | 3x30s | rest 45s

### Thu: Pull
- Australian rows | 3x8 | rest 90s
- Dead hang | 3x20s | rest 60s

## Weeks 5-8: Build

### Mon: Push
- Push-ups | 4x12 | rest 90s
- Plank | 3x45s | rest 45s

### Thu: Pull
- Australian rows | 4x10 | rest 90s
- Negative pull-ups | 3x5 | rest 120s | note: lower for 4 seconds

THE DAY-BY-DAY SHAPE

---
anything: 1
title: 14-Day Beginner Meditation
category: meditation
days: 14
---

## Days 1-7: Showing Up
Build the habit and learn to notice what's happening.

### Day 1: Just Breathe
Sit comfortably with your eyes closed. Feel the breath move in and out. When the mind wanders, gently come back.
- Breath awareness | 5m

### Day 2: Body Scan
Move your attention slowly from head to toes, noticing without changing anything.
- Body scan | 5m

(continue with Day 3 to Day 7)

## Days 8-14: Going Deeper

### Day 8: Longer Breath
- Breath awareness | 10m | note: restlessness around minute five is normal

(continue with Day 9 to Day 14)

RULES

1. The header sits between two lines that are exactly three dashes. It has four keys, one per line: anything, title, category, and then weeks (weekly shape) or days (day-by-day shape). Category is a short word: workout, meditation, study, habit.
2. Phase headings cover the whole plan exactly once, with no gaps and no overlaps: "## Weeks 1-4: Name" in the weekly shape, "## Days 1-7: Name" in the day-by-day shape.
3. Day headings: "### Mon: Title" using only Mon Tue Wed Thu Fri Sat Sun, each at most once per phase. Or "### Day 9: Title", each number once in the whole plan. A day with nothing to do is left out — the app shows it as rest.
4. One or two plain sentences may follow a phase heading or a day heading. Use them for the goal of the phase or the instructions for the day. Nothing else may be prose.
5. Item lines start with "- " and use the pipe character between fields:
   - Name | amount | rest 60s | note: short cue
   Only "Name | amount" is required. "rest" and "note" are optional, in that order.
6. The amount is either sets x reps — "3x10", "3x30s", "1xmax", "3x10 each leg" — or a single amount of time or count — "10m", "30s", "20". Never "3 sets of 10" or "3 x 10 reps".
7. No other fields and no other structure. No tables, no sub-headings, no numbered lists, no "Tips", "Notes" or "Troubleshooting" section at the end. Anything worth saying belongs in a day's sentence or an item's note, or not at all.

CHECK BEFORE YOU ANSWER

- Is it one shape all the way through — only weekdays, or only day numbers?
- Does the header say weeks for the weekly shape and days for the day-by-day shape?
- Do the phases cover the plan from 1 to the end, once each?
- Does every item line have a name, then a pipe, then an amount?
- Is there any bold, any table, any extra heading, or any sentence outside the shape? Remove it.
- For a day-by-day plan: is every day written out? Do not leave "(continue ...)" lines in your answer.

MY PLAN REQUEST

`
