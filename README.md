# DietBuilder

AI-powered dietary planning application that generates personalized meal plans based on your health profile, exercise routine, goals, and available foods. Built with Spring Boot, MongoDB, Next.js, and OpenAI GPT.

## Architecture

| Layer    | Technology                 | Port |
|----------|----------------------------|------|
| Frontend | Next.js + Tailwind CSS     | 3000 |
| Backend  | Spring Boot 3.2            | 8080 |
| Database | MongoDB                    | 27017|
| AI       | OpenAI (see modes below)   | —    |

**Plan generation modes**

- **Hybrid (recommended)** — Deterministic pipeline: curated USDA foods from Mongo (`FoodItem`), slot assembly, calorie scaling, server-side nutrient audit and safety. Optional small LLM pass for titles/rationale (`gpt-4o-mini` by default); expert depth adds a separate evidence-tags call. Lower completion tokens and latency than full JSON generation.
- **Monolith (legacy)** — Single large JSON completion from the primary model (`OPENAI_MODEL`, default `gpt-4o`) with full multi-day structure; optional parallel chunks via `OPENAI_PLAN_CHUNK_DAYS`.

Logs: rolling file at `backend/logs/dietbuilder.log` (see `logging.file.name`). Recommendation timing is logged as `recommendation.done` with `rag_retrieval_ms`, `openai_total_ms`, etc.; OpenAI calls log `prompt_build_ms`, `chat_completion_ms`, and token usage.

## Prerequisites

- **Java 17+**
- **Node.js 18+** and npm
- **MongoDB** running locally (or a connection URI)
- **OpenAI API key** ([platform.openai.com](https://platform.openai.com))

## Quick Start

### 1. Clone and enter the repo

```bash
git clone https://github.com/aryanchadha4/DietBuilder.git
cd DietBuilder
```

### 2. Start MongoDB

If using Homebrew on macOS:

```bash
brew services start mongodb-community
```

Or with Docker:

```bash
docker run -d -p 27017:27017 --name dietbuilder-mongo mongo:7
```

### 3. Start the Backend

```bash
cd backend
export OPENAI_API_KEY=sk-your-key-here
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080/api`.

### 4. Start the Frontend

```bash
cd frontend
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) in your browser.

## Environment Variables

### Backend (`backend/`)

| Variable         | Default                          | Description              |
|------------------|----------------------------------|--------------------------|
| `MONGODB_URI`    | `mongodb://localhost:27017/dietbuilder` | MongoDB connection URI   |
| `OPENAI_API_KEY` | (required)                       | Your OpenAI API key      |
| `OPENAI_MODEL`   | `gpt-4o`                        | Model for monolith plan generation and replacements |
| `OPENAI_MAX_TOKENS` | `16384` (see `application.yml`) | Upper bound on completion size |
| `OPENAI_PLAN_MODE` | `monolith` | `monolith` or `hybrid` (API can override per request) |
| `OPENAI_PLAN_HYBRID_DEPTH` | `detailed` | `fast` (no polish LLM), `detailed` (titles/rationale), `expert` (+ evidence call) |
| `OPENAI_PLAN_CHUNK_DAYS` | `0` | If &gt; `0`, split monolith plans into parallel segments of this many days |
| `OPENAI_PLAN_HYBRID_POLISH_MODEL` | `gpt-4o-mini` | Model for hybrid polish (and expert evidence helper) |
| `OPENAI_PLAN_MEAL_BANK_CACHE_TTL_MINUTES` | `60` | Meal-bank cache TTL (hybrid) |
| `LOG_FILE` | `logs/dietbuilder.log` | Log file path when running from `backend/` |

See `backend/src/main/resources/application.yml` for RAG embedding and retrieval settings.

### Frontend (`frontend/`)

| Variable               | Default                  | Description       |
|------------------------|--------------------------|-------------------|
| `NEXT_PUBLIC_API_URL`  | `http://localhost:8080/api` | Backend API URL |

## API Endpoints

### Profiles
- `GET    /api/profiles` — List all profiles
- `GET    /api/profiles/:id` — Get a profile
- `POST   /api/profiles` — Create a profile
- `PUT    /api/profiles/:id` — Update a profile

### Diet Plans
- `POST   /api/recommend/:profileId` — Generate a diet plan. Query params: `days` (default 14), `cuisines` (repeatable), `planMode` (`monolith` \| `hybrid`), `hybridDepth` (`fast` \| `detailed` \| `expert`), `syncDays` (optional: return only the first *N* days for progressive loading; `totalDays` stays the requested length).
- `POST   /api/diet-plans/:planId/complete-days` — Append remaining days for a partial hybrid plan (`days.length` &lt; `totalDays`).
- `GET    /api/diet-plans/:profileId` — List plans for a profile
- `GET    /api/diet-plans` — List all plans

### Tasks
- `GET    /api/tasks` — List all tasks
- `GET    /api/tasks/:id` — Get a task
- `POST   /api/tasks` — Create a task
- `PUT    /api/tasks/:id` — Update a task
- `DELETE /api/tasks/:id` — Delete a task

## How It Works

1. **Create a health profile** with your physical stats, exercise schedule, dietary restrictions, medical info, goals, and available foods.
2. **Generate a diet plan**
   - **Hybrid:** BMR/TDEE and calorie targets are computed on the server (`CalorieTargetCalculator`). Foods come from the curated `FoodItem` bank and cultural groups; meals are assembled per slot and scaled to the daily target; micronutrient adequacy is computed with `NutrientReferenceService` (not from the model). An optional short OpenAI call polishes labels and copy; “expert” adds evidence-oriented tags using retrieved sources.
   - **Monolith:** The backend builds a detailed user message (profile, DRI summary, cuisines, RAG evidence snippets) and requests a full multi-day JSON plan from the configured model. Chunking can parallelize long plans.
3. **Review your plan** — multi-day view with calories and macros; the Recommend UI can choose **Hybrid** vs **Full AI**, depth (fast / detailed / expert), and optional **sync days** for progressive plans.
4. **Track tasks** — use the built-in task manager to track meal prep, grocery shopping, and health milestones.

## Tech Stack Details

- **Spring Boot 3.2** with Spring Data MongoDB, Bean Validation, WebFlux (for OpenAI calls)
- **MongoDB** for flexible document storage (profiles, plans, foods, expert sources, embeddings)
- **Next.js 16** with App Router, Server Components, and Tailwind CSS v4
- **OpenAI** — JSON mode for monolith and hybrid polish; hybrid assembly avoids large structured completions
- **RAG** — embeddings + retrieval over expert sources (`ExpertKnowledgeService`); query caching and meal-bank keyword weighting in hybrid mode
- **Logging** — `logback-spring.xml` configures console + file appenders; request IDs in MDC where applicable
- **Lucide React** for icons, **react-hot-toast** for notifications
