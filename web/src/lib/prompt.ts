/**
 * The prompt users paste into their AI before asking for a plan.
 * It makes the AI answer in the Anything format, so the plan parses first time.
 * Keep it in sync with the parser rules on the server.
 */
export const ANYTHING_PROMPT = `Write my plan in the "Anything" format so I can track it in an app.
Follow these rules exactly and output only the plan, nothing else:

1. Start with this header (fill in the values):
---
anything: 1
title: <short plan name>
category: workout
weeks: <total number of weeks>
---
2. Split the plan into phases using headings like "## Weeks 1-4: Phase Name".
   The phases must cover every week from 1 to the total, with no gaps or overlaps.
3. Inside each phase, list training days as "### Mon: Day Title"
   (Mon, Tue, Wed, Thu, Fri, Sat or Sun). Leave rest days out.
   Use each weekday at most once per phase.
4. List each exercise on its own line as:
   - Exercise name | sets x reps | rest 60s | note: short tip
   Reps can be a number (10), a time (30s) or text (10 each leg).
   "rest" and "note" are optional.
5. No tables, no extra headings, no explanations outside this structure.

My plan request:
`
