# Anything

> Your AI wrote the plan. Anything runs it.

Paste an AI-written plan and Anything turns it into a calm daily tracker. See `BLUEPRINT.md` for the full build plan.

## Project layout

```
anything/
├── BLUEPRINT.md        day-by-day build plan
├── FRICTION.md         daily log of what annoys you (from Day 15)
├── docker-compose.yml  local PostgreSQL
├── fixtures/           sample plans in the Anything format
├── server/             Spring Boot API (Java 21)
└── web/                React + TypeScript + Vite + Motion
```

## Run it locally

You need Java 21 or newer and Node 20+. Maven downloads itself the first time you run `./mvnw`. Docker is optional: by default the server uses an H2 database file in `server/data/`.

```bash
# Terminal 1: server
cd server
./mvnw spring-boot:run

# Terminal 2: web app
cd web
npm install
npm run dev
```

To use real PostgreSQL instead (needs Docker):

```bash
docker compose up -d
cd server && ./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

Open http://localhost:5173. The card should say **Server connected**.

Check the server on its own:

```bash
curl http://localhost:8080/api/health
# {"status":"ok","app":"anything"}
```

Run the server tests (no database needed):

```bash
cd server && ./mvnw test
```
