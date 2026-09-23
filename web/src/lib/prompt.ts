/**
 * The prompt users paste into their AI before asking for a plan.
 *
 * The first version described the format and hoped. It lost on the things
 * models do by reflex: a friendly sentence before the plan, a code fence around
 * it, **bold** on every exercise name, "Monday" where the parser wants "Mon",
 * "3 sets x 10 reps" where it wants "3x10", and a tips section at the end.
 *
 * What changed: a complete worked example (a model copies a shape far more
 * reliably than it follows a description), the failure modes named out loud as
 * bans rather than left implicit, and a checklist to run before answering —
 * every line of which maps to a real parser error.
 *
 * Keep it in sync with the parser rules on the server.
 */
export const ANYTHING_PROMPT = `You are writing a training plan in the "Anything" format. An app reads it line by line and turns it into a daily checklist: every day heading becomes one screen, every exercise line becomes one tick box. A line it can't read is an error the person has to fix by hand, so the format matters more than the prose.

Output the plan and nothing else. No sentence before it, no summary after it, no code block around it, and no bold, italic or emoji anywhere in it.

THE SHAPE

---
anything: 1
title: <short name, under 60 characters>
category: workout
weeks: <total number of weeks, digits only>
---

## Weeks 1-4: Phase Name
### Mon: Day Title
- Exercise name | 3x10 | rest 60s | note: one short cue

RULES

1. The header block sits between two lines that are exactly three dashes. It has those four keys, in that order, one per line. Nothing else goes in it.
2. Phase headings are "## Weeks 1-4: Name" or "## Week 5: Name". Together they must cover every week from 1 to the total exactly once — no gaps, no overlaps, no week past the total.
3. Day headings are "### Mon: Title". Use the three-letter day only: Mon, Tue, Wed, Thu, Fri, Sat, Sun. Each day may appear at most once per phase.
4. There are no rest days in the file. A day with nothing to do is simply left out, and the app shows it as rest.
5. Exercise lines start with "- " and use the pipe character to separate fields:
   - Name | sets x reps | rest 60s | note: cue
   Only "name | sets x reps" is required. "rest" and "note" are optional, in that order.
6. Sets is a plain number, 1 to 20. Reps is a number (10), a time (30s), the word max, or short text (10 each leg). Write "3x10", never "3 sets of 10" or "3 x 10 reps".
7. Rest is written "rest 60s" or "rest 2m". Note is written "note: keep your back flat".
8. No other fields. No tempo, no RPE, no weight column, no tables, no sub-headings, no blank-line sections, no "Notes" or "Tips" at the end. Anything extra belongs in a note, or not at all.

A COMPLETE EXAMPLE — copy this shape exactly

---
anything: 1
title: 8-Week Beginner Strength
category: workout
weeks: 8
---

## Weeks 1-4: Foundation

### Mon: Push
- Push-ups | 3x8 | rest 90s | note: elbows tucked at 45 degrees
- Bench dips | 3x10 | rest 60s
- Plank | 3x30s | rest 45s

### Thu: Pull
- Australian rows | 3x8 | rest 90s | note: squeeze at the top
- Dead hang | 3x20s | rest 60s

## Weeks 5-8: Build

### Mon: Push
- Push-ups | 4x12 | rest 90s
- Pike push-ups | 3x8 | rest 90s
- Plank | 3x45s | rest 45s

### Thu: Pull
- Australian rows | 4x10 | rest 90s
- Negative pull-ups | 3x5 | rest 120s | note: lower for 4 seconds

CHECK BEFORE YOU ANSWER

- Does the first line have exactly three dashes, and is the header closed by another?
- Do the phases cover weeks 1 to the total, once each?
- Is every day heading one of Mon Tue Wed Thu Fri Sat Sun, and used at most once per phase?
- Does every exercise line have a name, then a pipe, then sets x reps?
- Is there any bold, any table, any heading or sentence that isn't in the shape above? Remove it.

MY PLAN REQUEST

`
