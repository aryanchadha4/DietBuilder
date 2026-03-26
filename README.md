# DietBuilder

AI-powered dietary planning application that generates personalized meal plans based on your health profile, exercise routine, goals, and available foods. Built with Spring Boot, MongoDB, Next.js, and OpenAI GPT.

## Architecture

| Layer    | Technology                 | Port |
|----------|----------------------------|------|
| Frontend | Next.js + Tailwind CSS     | 3000 |
| Backend  | Spring Boot 3.2            | 8080 |
| Database | MongoDB                    | 27017|
| AI       | OpenAI GPT-4o              | —    |

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
| `OPENAI_MODEL`   | `gpt-4o`                        | OpenAI model to use      |
| `OPENAI_MAX_TOKENS` | `4096`                       | Max tokens per response  |

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
- `POST   /api/recommend/:profileId` — Generate a new diet plan
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
2. **Generate a diet plan** — the system sends your profile to OpenAI GPT with an expert nutrition system prompt that uses evidence-based guidelines (USDA, WHO, ISSN) and the Mifflin-St Jeor equation for BMR/TDEE calculation.
3. **Review your plan** — see a daily meal breakdown with exact calories and macro targets, complete with expert notes.
4. **Track tasks** — use the built-in task manager to track meal prep, grocery shopping, and health milestones.

## Tech Stack Details

- **Spring Boot 3.2** with Spring Data MongoDB, Bean Validation, WebFlux (for OpenAI calls)
- **MongoDB** for flexible document storage
- **Next.js 16** with App Router, Server Components, and Tailwind CSS v4
- **OpenAI GPT-4o** with JSON mode for structured diet plan responses
- **Lucide React** for icons, **react-hot-toast** for notifications
