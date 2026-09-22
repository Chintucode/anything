# Anything: Build Blueprint

> **Your AI wrote the plan. Anything runs it.**

Put this file at the root of your repo. At the start of each session, open it, find today's day, and tell Claude "Day N".

---

## 0. The polished idea

**The problem:** AI writes excellent plans for workouts, study and routines. Then the plan sits in a chat window and never gets done.

**The product:** Paste an AI-written plan and Anything turns it into a calm daily tracker. When you want to adjust, Anything writes a progress report you paste back into your AI, and the AI updates the plan.

```
   AI writes plan  ──►  Anything runs it daily  ──►  Progress report
        ▲                                               │
        └──────────── paste back, AI adjusts ◄──────────┘
```

**What changed from the original idea, and why:**

| Before | After | Why |
|---|---|---|
| Upload a file | **Paste text** (file upload as a fallback) | Pasting takes 2 taps on a phone. Downloading and uploading takes 8. |
| Pick a category button at upload | Category is read from the plan header | One less decision, and the AI already knows what it wrote. |
| Tracker only | **Tracker + "Report for your AI" export** | This round trip is the moat. Habit trackers are everywhere, but none of them close the loop with your AI. It also costs you nothing, because users bring their own AI. |
| A missed day = broken plan | **Skip or Shift** when you miss a day | Real life happens. Plans that punish one missed day get deleted. |
| Two UI modes at launch | Apple mode first, Solo Leveling later | Build one mode really well. Solo Leveling becomes a shareable growth feature once the core works. |

**Version one's promise:** go from an AI chat to a tracked plan in **under 3 minutes**, then open the app daily in **under 5 seconds**.

---

## 1. Stack

| Layer | Choice | Notes |
|---|---|---|
| Backend | Spring Boot + Java 21 | Your strongest skill, and it's what recruiters look for |
| Database | PostgreSQL (Docker locally), Flyway migrations | H2 is fine only for tests |
| Frontend | React + TypeScript + Vite | |
| Motion | Motion (formerly Framer Motion): `npm i motion`, `import { motion } from "motion/react"` | Springs, layout animations, gestures |
| Design | Your `apple-design` skill | Load it on every UI day |
| App shell | PWA (manifest + service worker) | Installs on your phone, no app store |
| Deploy (week 4) | Backend on Render/Railway, frontend on Vercel/Netlify | Free tiers are enough |

**Repo layout:**

```
anything/
├── BLUEPRINT.md
├── FRICTION.md          ← daily log of what annoyed you (starts Day 15)
├── fixtures/            ← sample plans, including broken ones
├── server/              ← Spring Boot
└── web/                 ← React + Vite
```

---

## 2. Rules for every day

1. **Aim for about 2 focused hours.** If a day runs over, it spills into the next one, and that's fine.
2. **A day is done when the goal works, is committed and is pushed.** It doesn't have to be pretty.
3. **The parser always has tests.** It's the heart of the product.
4. **From Day 15 on, you use the app for your real workout every day.** Whatever annoys you goes in `FRICTION.md`.
5. **Fix one friction per day before building anything new.**

---

## 3. Week 1: The engine (backend)

**Goal: a plan goes in and the correct "today" comes out, proven by tests.**

### Day 1: Setup
- [ ] Create the repo and folders as above; run `git init`
- [ ] Generate `server/` with Spring Initializr: Web, Validation, Data JPA, PostgreSQL, Flyway, Lombok (optional)
- [ ] Generate `web/` with `npm create vite@latest web -- --template react-ts`
- [ ] Add `docker-compose.yml` with Postgres
- [ ] Rewrite **your own calisthenics plan** in the Anything format and save it as `fixtures/calisthenics.md`
- **Done when:** both apps start and the fixture exists.

### Day 2: Parser, part 1 (header and phases)
- [ ] `PlanParser.parse(String markdown) → ParseResult`
- [ ] Read the header block: `anything`, `title`, `category`, `weeks`
- [ ] Read `## Weeks A-B: Name` and `## Week N` headings
- [ ] Write tests for a valid header, a missing header, a missing field and a bad week range
- **Done when:** the header and phase tests pass.

### Day 3: Parser, part 2 (days and exercises)
- [ ] Read `### Mon: Title` headings
- [ ] Read exercise lines: `name | sets x reps | rest | note:`
- [ ] Reps can be a count (`10`), a time (`30s`) or text (`10 each leg`)
- [ ] Every error carries a line number and a plain-English message
- [ ] Add 5 broken fixtures and a test for each
- **Done when:** `calisthenics.md` parses fully and every broken fixture fails with a clear message.

### Day 4: Validation and the parse endpoint
- [ ] Phases must cover weeks 1 to N with no gaps or overlaps
- [ ] No duplicate weekday inside a phase
- [ ] `POST /api/plans/parse` takes raw text and returns either a preview or a list of errors, saving nothing
- [ ] Add a global exception handler that returns clean JSON errors
- **Done when:** you can parse a plan with curl or Postman.

### Day 5: Saving plans
- [ ] Entities: `Plan`, `PlanPhase`, `PlanDay`, `PlanItem` (keep `rawMarkdown` on `Plan`)
- [ ] Flyway `V1__init.sql`
- [ ] `POST /api/plans` (text + start date), `GET /api/plans`, `DELETE /api/plans/{id}`
- **Done when:** a plan survives a server restart.

### Day 6: The schedule engine
- [ ] `ScheduleService.dayFor(plan, date)` works out the week number, then the phase, then the `PlanDay` (or a rest day)
- [ ] Add a `dayOffset` field on `Plan`, used by Shift later
- [ ] Test the edges: before the start date, the last day, after the end, a rest day, a phase boundary
- [ ] `GET /api/plans/{id}/today` (accepts a `?date=` override for testing)
- **Done when:** the date-boundary tests pass.

### Day 7: Completions, progress and a buffer
- [ ] `Completion` entity plus `PUT /api/plans/{id}/completions`
- [ ] `GET /api/plans/{id}/progress` returns completion % *up to today*, the current streak and weekly bars
- [ ] Catch up on anything that slipped this week
- **Done when:** you can tick an item with curl and watch the percentage move.

---

## 4. Week 2: The Apple-mode app (frontend)

**Goal: the app is installed on your phone, and you tick off a real workout with it.**
Load the `apple-design` skill every day this week.

### Day 8: Design foundation
- [ ] Design tokens as CSS variables: type scale (size-specific tracking), colors (light and dark), spacing, radii, materials (translucent blur)
- [ ] App shell: a large-title header and a tab bar (Today · Plans)
- [ ] Routing and an API client (`fetch` wrapper plus TanStack Query)
- [ ] Set up Motion with shared spring presets, e.g. `{ type: "spring", stiffness: 400, damping: 30 }`
- **Done when:** the empty app looks calm and native in both themes.

### Day 9: Add a plan (paste flow)
- [ ] A big paste area, with "Upload file" as a secondary option
- [ ] A **"Copy the prompt"** button that copies the Anything prompt with one tap
- [ ] On paste, call `/parse` and show the preview (phases, then days, then exercises) or the errors inline by line
- **Done when:** pasting your plan shows a correct preview.

### Day 10: Confirm and the plans list
- [ ] A start-date picker (defaults to next Monday) and a Confirm button
- [ ] The plans list, with a progress ring on each plan and swipe-to-delete (velocity-aware)
- **Done when:** you can add and delete plans end to end.

### Day 11: The Today screen (the most important screen)
- [ ] Today's title (e.g. "Week 3 · Push") and the exercise rows with sets and reps
- [ ] Tap to complete: a spring check, a quiet strike-through, and the row settles into place
- [ ] Rest day: a calm "Rest day" state that shows the next workout
- **Done when:** ticking off a full workout feels satisfying.

### Day 12: Progress
- [ ] A progress ring that animates on open, the streak, and weekly bars
- [ ] Completing the last item of the day gets one subtle celebration, not confetti
- **Done when:** progress looks right against the backend numbers.

### Day 13: Missed days (Skip or Shift)
- [ ] If yesterday's workout wasn't finished, show a gentle card: **Skip it** or **Shift the plan by one day**
- [ ] Shift increments `dayOffset`; Skip marks the day as skipped, and skipped days don't hurt the percentage
- **Done when:** missing a day never breaks the plan.

### Day 14: PWA and moving in
- [ ] Manifest, icons and a service worker (`vite-plugin-pwa`); cache today's plan for offline use
- [ ] Run it on your phone over the local network (or a tunnel)
- [ ] Install it on your home screen
- **Done when:** your next real workout is tracked in Anything.

---

## 5. Week 3: Dogfood and polish

**Goal: you'd pick this over a notes app, every day.**
From today, each session starts with: **read `FRICTION.md` → fix the top item → then do the day's task.**

### Day 15: Log actual reps
- [ ] Long-press or expand a row to enter what you actually did (e.g. 8 of 10)
- **Done when:** the actual reps are saved and shown.

### Day 16: Week view
- [ ] Swipe between days; a week strip shows done, partial, missed and rest days
- **Done when:** you can check any past or future day.

### Day 17: Motion pass (apple-design skill)
- [ ] Every animation is a spring and can be interrupted mid-flight
- [ ] Shared layout transition from a plan card into the plan detail view
- [ ] Gestures hand their release velocity into the spring
- [ ] Respect `prefers-reduced-motion`
- **Done when:** nothing snaps, stutters or waits for an animation to finish.

### Day 18: "Report for your AI" (the round-trip feature)
- [ ] One button generates a text summary: completion %, missed days, exercises where actuals were below target, and which week you're on
- [ ] It ends with a ready-made ask: *"Adjust the plan for the remaining weeks based on this. Output it in the Anything format."*
- [ ] Copy it, paste it into your AI, paste the result back, and the app offers **"Replace plan, keep history"**
- **Done when:** you've done the full loop once with a real AI.

### Day 19: Empty states, errors and copy
- [ ] Every screen has a kind empty state and a useful error message
- [ ] Rewrite the parser error messages so a non-developer understands them
- **Done when:** a friend could recover from any error without asking you.

### Day 20: Accessibility and theme check
- [ ] Contrast, tap targets of at least 44px, labels for screen readers, dark mode everywhere
- **Done when:** the app passes a quick accessibility audit.

### Day 21: Auth
- [ ] Simple accounts: Spring Security with email magic links or Google sign-in
- [ ] Every plan belongs to a user
- **Done when:** two accounts can't see each other's plans.

---

## 6. Week 4: Ship and validate

**Goal: find out whether other people want this too.**

### Day 22: Deploy
- [ ] Backend and Postgres on Render/Railway, frontend on Vercel/Netlify, with environment variables and CORS set up
- **Done when:** a public URL works on your phone over mobile data.

### Days 23-24: Watch 3-5 people try it
- [ ] Friends from your PG or old classmates: give them the link and say nothing
- [ ] Time how long they take to get from an AI chat to a tracked plan (the target is under 3 minutes)
- [ ] Write down every place they hesitate
- **Done when:** you have a list of real friction from real people.

### Day 25: Fix onboarding
- [ ] Fix the top 3 problems from the testing sessions
- [ ] Add a sample plan so people can try the app before making their own

### Day 26: Portfolio and build-in-public
- [ ] README with screenshots, a demo GIF, the architecture and the parser design
- [ ] Post a demo video on LinkedIn and your YouTube channel: "I built an app that runs your AI's plans"

### Days 27-28: Decide what's next, from the evidence
Pick **one**, based on what the testers asked for:
- **Study category** (topics and sessions instead of sets and reps)
- **Solo Leveling mode** (stats, levels, a "system" voice; very shareable)
- **Reminders** (push notifications through the PWA)

---

## 7. After version one: the improvement loop

Repeat this every week:

1. **Monday:** read `FRICTION.md` and the user feedback, and pick the week's single focus.
2. **Tuesday to Thursday:** build it.
3. **Friday:** polish and ship.
4. **Weekend:** use the app, watch one new person use it, write down the friction.

**Numbers worth tracking:**
- Time from paste to a tracked plan
- Day-7 retention (are people still ticking items a week later?)
- How many people use "Report for your AI" (this proves the round trip)

---

## 8. Working with Claude on this

- Start each session with: "Anything, Day N" and paste in the day's section.
- UI days: ask Claude to load the `apple-design` skill (and your Motion skill once it's added).
- Backend days: ask for tests first, then the code.
- If you get stuck for more than 30 minutes, paste the error and the file. Don't lose a whole day to it.
