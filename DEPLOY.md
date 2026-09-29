# Putting Anything on the internet

One URL serves both the app and the API. It runs for free, on two services:

| What | Where | Free tier, as of September 2026 |
|---|---|---|
| The app (a Docker image built from this repo) | Render, Singapore | Sleeps after 15 idle minutes; the first visit after that takes about a minute |
| The database (PostgreSQL) | Neon, Singapore | 0.5 GB; sleeps after 5 idle minutes and wakes in about a second; never deleted |

Why not Render's own database: Render deletes a free database 30 days after it's created.

> **Before you share the link:** there are no accounts yet. Anyone who has the URL
> sees the same plans and can tick or delete them. Keep the link to yourself until
> magic-link sign-in is built.

## 1. The database (Neon), about 3 minutes

1. Sign up at https://neon.com. GitHub sign-in is fine.
2. Create a project:
   - **Name:** `anything`
   - **Region:** AWS Asia Pacific (Singapore). It must be the same region as the app, or every tap waits for a round trip across the world.
3. On the project dashboard, press **Connect**. Turn **Connection pooling off**, then copy the connection string. It looks like this:

   ```
   postgresql://neondb_owner:npg_...@ep-something-123456.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require
   ```

   Paste the whole string, exactly as copied. The app takes it apart itself (see `server/.../config/DatabaseUrl.java`). This string is a password, so never commit it and never paste it into chat.

## 2. The app (Render), about 5 minutes plus a 5–10 minute first build

1. Push this code to GitHub first; Render builds whatever is on `main`.
2. If you already made a web service for this repo on Render, delete it (**Settings → Delete Web Service**). The Blueprint below creates a correctly set up one.
3. In the Render dashboard: **New → Blueprint**, then pick the `anything` repository.
4. Render reads `render.yaml` and shows one web service, **anything**, on the **Free** plan. It asks for `DATABASE_URL`: paste the Neon string from step 1.
5. Press **Apply**. The first build downloads everything, so it takes a while. Watch the logs. It's ready when you see:

   ```
   Migrating schema "public" to version "6 - day by day plans"
   Started AnythingApplication in ... seconds
   ```

6. Open `https://anything-xxxx.onrender.com` (your URL is shown at the top of the service page). Then check `/api/health`, which should return `{"status":"ok","app":"anything"}`.

## 3. On your phone

Open the URL in Safari, then **Share → Add to Home Screen**. It opens full-screen like an app and keeps working offline for the day you're on.

## Every change after this

```bash
cd server && ./mvnw test
cd .. && git add -A && git commit -m "What changed" && git push
```

Every push to `main` builds and deploys by itself. If a build fails, the version already running stays up.

## When something goes wrong

| You see | It means |
|---|---|
| Build log: `npm ci` fails | `web/package-lock.json` is out of date. Run `npm install` in `web/`, then commit the lock file |
| `DATABASE_URL is not set` | Add it in the service's **Environment** tab, then redeploy |
| `DATABASE_URL should start with postgresql://` | Something other than Neon's connection string was pasted |
| `password authentication failed` | The password was reset on Neon. Copy the string again |
| The first open of the day hangs for a minute | That's the free server waking up. Normal |
| Your phone shows an old version | Close the app completely and open it again. The new version loads on the second open |

## Try the production setup on your Mac

This is the same profile Render runs, but pointed at any Postgres database:

```bash
cd server
SPRING_PROFILES_ACTIVE=prod DATABASE_URL='postgresql://...' ./mvnw spring-boot:run
```
