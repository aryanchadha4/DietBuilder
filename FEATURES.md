# Features

## Rigorous, AI-Powered Nutrition Engine

DietBuilder treats the LLM as one component in a multi-step pipeline — not the entire engine. Every plan passes through pre-generation safety screening, nutrient auditing against NIH reference data, post-generation guardrails, evidence classification, and full audit logging before it reaches the user.

---

### 1. Auditable Nutrient Database (Hybrid Architecture)

Meal plans are backed by real nutrient data, not LLM estimates.

**Curated local database**
- ~150 common foods seeded on first startup from `curated-foods.json`, each with full nutrient profiles (energy, protein, fat, carbohydrate, fiber, calcium, iron, magnesium, zinc, vitamins A/C/D/E/K/B12, folate, potassium, sodium)
- Every food carries an `fdcId` (USDA FoodData Central identifier), `culturalTags`, and `allergenTags`
- MongoDB text indexes on description and category for fast search

**USDA API fallback**
- When a local search returns no results, the system queries the USDA FoodData Central REST API
- Results are automatically cached locally so subsequent lookups are instant
- Configured via `USDA_API_KEY` environment variable; gracefully degrades if unset

**Nutrient reference data**
- `nutrient-references.json` encodes NIH/IOM Dietary Reference Intakes (RDA) and Tolerable Upper Intake Levels (UL) for every tracked nutrient
- References are stratified by age range and sex
- `NutrientReferenceService` provides programmatic lookup: `getDRI(nutrient, age, sex)`, `getUL(nutrient, age, sex)`

**Nutrient audit on every plan**
- After LLM generation, `enrichWithNutrientAudit()` aggregates `keyNutrients` from every `MealFood` in the plan
- Each nutrient is classified as **DEFICIENT** (<50% RDA), **LOW** (50-80% RDA), **ADEQUATE**, or **EXCESSIVE** (>UL)
- An overall adequacy score (percentage of nutrients meeting RDA) is attached to the plan

---

### 2. Safety Guardrails and Red-Flag Detection

Safety checks are deterministic rules, not AI judgment. They run both before and after plan generation.

**Pre-generation checks** (`SafetyGuardrailService.runPreChecks`)

| Check | Trigger | Severity |
|-------|---------|----------|
| ED risk — underweight + weight loss | BMI < 18.5 and goals include weight loss | **BLOCK** |
| ED risk — history | Medical info mentions eating disorder, anorexia, or bulimia | **BLOCK** |
| Unsafe weight loss | Aggressive weight/timeline targets detected | WARNING |
| Minor restriction | User is under 18 | WARNING |

**Post-generation checks** (`SafetyGuardrailService.runPostChecks`)

| Check | Trigger | Severity |
|-------|---------|----------|
| Very Low Calorie Diet | Plan < 1200 kcal (WARNING) or < 800 kcal (**BLOCK**) | WARNING / BLOCK |
| Extreme macros | Protein > 45% or fat > 65% of calories | WARNING |
| UL enforcement | Any nutrient exceeds Tolerable Upper Intake Level | WARNING |

**Blocked plans** are saved with safety alerts but no meal content. The UI renders a prominent red banner directing the user to consult a healthcare provider.

**All safety alerts are persisted** to the `safety_alerts` collection for longitudinal tracking and fairness auditing.

---

### 3. Evidence Classification and Transparency

Every plan distinguishes what is well-supported from what is inferred.

**Evidence levels on claims**
- `GUIDELINE_BACKED` — sourced from NIH DRI, USDA Dietary Guidelines, ADA Standards of Care, DASH, KDOQI, ACOG
- `META_ANALYSIS` — sourced from systematic reviews (e.g., ISSN protein position stand)
- `OBSERVATIONAL` — supported by cohort or observational studies
- `LOW_CONFIDENCE` — reasonable inference without strong direct evidence

**UI rendering**
- The `EvidenceBadge` component color-codes each level (green, blue, amber, gray)
- Hovering reveals the full claim, source citation, and explanation
- Evidence tags are grouped under an "Evidence Basis" section on each plan

---

### 4. Full Recommendation Audit Trail

Every plan generation event is logged for reproducibility and review.

**What gets logged** (`RecommendationLog`)
- `modelName` and `modelVersion` — which LLM was used
- `systemPrompt` — the exact system prompt sent
- `userMessage` — the user-specific prompt (PII redacted: names and emails replaced with `[REDACTED]`)
- `profileSnapshot` — anonymized demographic snapshot (age, gender, height, weight, goals, restrictions)
- `safetyChecksRun` — list of pre and post checks executed
- `postProcessingSteps` — pipeline stages applied (nutrient audit, safety guardrails, evidence tagging)
- `latencyMs` — end-to-end generation time
- `dataRetentionPolicy` — default 90 days

**UI rendering**
- `AuditLogViewer` is an expandable panel on the recommendation page showing model, latency, safety checks, and post-processing steps

---

### 5. Culturally Relevant Food Substitutions

Plans respect culinary traditions instead of defaulting to a generic Western diet.

**Cultural food groups**
- `cultural-food-groups.json` defines food categories (grain staple, protein source, vegetable, fruit, dairy alternative, legume, cooking fat, seasoning) for multiple culinary traditions: South Asian, East Asian, Southeast Asian, West African, East African, Latin American, Caribbean, Mediterranean, Middle Eastern, Northern European

**Substitution engine**
- Given a food and a target culture, `CulturalSubstitutionService` identifies the food's category and returns culturally appropriate equivalents with typical serving sizes
- Category inference uses food description and USDA category to map items (e.g., "rice" → `grain_staple`, "chicken" → `protein_source`)

**Profile integration**
- `culturalFoodPreference` field on `UserProfile` is passed to the LLM prompt so generated plans naturally use culturally appropriate foods
- The food search API accepts an optional `culture` filter

**API endpoints**
- `GET /api/foods/substitutions?fdcId=...&targetCulture=...` — find substitutions
- `GET /api/foods/cultures` — list supported culinary traditions

---

### 6. Privacy-Forward Design

Medical and dietary data is treated as sensitive from day one.

**Explicit consent**
- `ConsentRecord` tracks granular consent: `data_storage`, `ai_processing`, `outcome_analytics`, `fairness_research`
- AI plan generation is gated on `ai_processing` consent — requests without it are rejected with a clear message
- `ConsentDialog` component presents toggles on first use; `data_storage` and `ai_processing` are marked as required

**Data portability**
- `GET /api/privacy/export/:profileId` returns all user data (profile, plans, logs, alerts, outcomes, consent) as a single JSON document

**Right to deletion**
- `DELETE /api/privacy/data/:profileId` cascades deletion across all collections (profiles, plans, logs, alerts, outcomes, consent)

**Anonymization**
- `PrivacyService.anonymizeProfile()` replaces the user's name with `Anonymous-XXXXXX` and clears medical info
- Audit log snapshots are pre-anonymized (no name, no medical free text)

**IP hashing**
- Consent records store a truncated SHA-256 hash of the client IP, not the raw address

---

### 7. Longitudinal Adaptation

Plans evolve based on real-world results, not just initial inputs.

**Outcome recording**
- Users log weight, adherence percentage, symptoms (from a suggestion list), lab results (key-value pairs), training response (improving / stable / regressing), and free-text notes
- `OutcomeEntryForm` provides a structured form; entries are timestamped and linked to both profile and plan

**Trend computation**
- `OutcomeTrackingService.computeTrends()` calculates total weight change, average adherence, and symptom frequency across all records
- `WeightChart` renders an SVG line chart of weight over time

**Adaptation triggers** (`LongitudinalAdaptationService.shouldAdaptPlan`)

| Trigger | Threshold |
|---------|-----------|
| Low adherence | Average < 60% |
| Weight plateau | < 0.2 kg change over 5+ records |
| New symptoms | Symptoms in 2+ of last 3 records |
| Training regression | "Regressing" in 2+ of last 3 records |

**Adapted plan generation**
- When adaptation is recommended, the system generates a new plan with full context: previous plan, outcome trends, and adaptation reasons
- Adapted plans carry `parentPlanId` and an incremented `version` number
- All safety checks run on adapted plans identically to fresh plans

---

### 8. Fairness Audits

Recommendations are monitored for demographic disparities.

**Automated fairness reports** (`FairnessAuditService.generateReport`)
- Aggregates all plans and profiles, grouping by gender, age bracket (minor / young adult / adult / middle-aged / senior), and cultural food preference
- Per-group metrics: average adequacy score, average daily calories, safety alert rate
- **Disparity detection**: if any metric differs by more than 15 percentage points between the best and worst group, the gap is flagged

**UI**
- `/admin/fairness` page shows summary cards (plans analyzed, disparities flagged), detailed tables by demographic dimension, and flagged disparities with red badges
- Previous reports are listed for trend tracking

---

### 9. Offline Benchmark Testing

Safety logic is validated against expert-written test cases without calling the LLM.

**Benchmark cases** (`benchmark-cases.json`)
- Each case defines a synthetic user profile and expected safety flags (e.g., underweight + weight loss → `ED_RISK`, minor → `MINOR_RESTRICTION`)
- Cases cover eating disorder risk, minor restrictions, allergen handling, and healthy adult baselines

**Benchmark runner** (`BenchmarkService.runSafetyBenchmarks`)
- Runs `SafetyGuardrailService.runPreChecks` against each case
- Reports pass rate and **safety flag recall** (percentage of expected flags that were actually raised)

**JUnit tests** (`NutritionBenchmarkTest`)
- 7 unit tests covering: ED risk from underweight + weight loss, ED risk from history, minor warning, healthy adult passes, VLCD warning, VLCD block, extreme protein warning

**UI**
- `/admin/benchmarks` page provides a "Run Benchmarks" button with results showing total cases, pass rate, safety flag recall, and per-case expected vs. actual flags

---

### 10. Outcome-Based Rigor

Predicted outcomes are compared against actual results.

**Prediction vs. reality** (`OutcomeComparisonService.comparePredictedVsActual`)
- Retrieves a user's diet plans and outcome records
- Summarizes actual weight change, average adherence, and compares against the predicted adequacy score and calorie targets
- Available via `/api/admin/outcomes/comparison/:profileId`

**Retraining gate**
- Adapted plans are only generated after safety and bias review (pre-checks and post-checks run identically)
- Fairness audits must be reviewed before systematic changes to the generation pipeline

---

### Generation Pipeline

Plan generation follows a six-stage pipeline with stage-specific loading messages in the UI:

1. **Consent gate** — verify `ai_processing` consent exists
2. **Pre-generation safety checks** — ED risk, unsafe weight loss, minor restriction (BLOCK or WARN)
3. **LLM generation** — OpenAI receives the user profile with cultural preferences, exercise schedule, restrictions, and medical info
4. **Nutrient audit** — aggregate `keyNutrients` from every `MealFood`, validate against DRI/UL
5. **Post-generation safety checks** — VLCD detection, extreme macros, UL enforcement
6. **Persistence and logging** — save plan, persist safety alerts, log full audit trail

---

### 4-Step Profile Wizard

The profile creation form walks users through four focused steps:

1. **Personal Info** — name, age, gender, race/ethnicity, height, weight
2. **Exercise** — strength training and cardio schedules (days/week, type, duration)
3. **Diet & Medical** — cultural food preference, dietary restrictions (tag input with suggestions), medical information (free text covering conditions, allergies, medications, supplements)
4. **Goals & Foods** — goals (tag input with suggestions), available ingredients

---

### 11. Authentication & User Scoping

User accounts with session-based authentication. Every profile and diet plan is scoped to the authenticated user.

**Backend (Spring Security)**
- `User` MongoDB document with `username` (unique), `email` (unique), and BCrypt-hashed password (`@JsonIgnore` — never returned in API responses)
- `AuthService` implements Spring Security's `UserDetailsService` for authentication against MongoDB
- `SecurityConfig` uses session-based auth (`SessionCreationPolicy.IF_REQUIRED`) with `JSESSIONID` cookies
- `/api/auth/**` endpoints are public; all other `/api/**` endpoints require authentication
- Unauthenticated requests receive a `401` JSON response instead of an HTML redirect
- `PasswordEncoder` bean (BCrypt) is extracted to a standalone config to avoid circular dependencies

**Auth endpoints** (`AuthController`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/auth/register` | POST | Create account with username, email, password |
| `/api/auth/login` | POST | Authenticate, create session, return user |
| `/api/auth/logout` | POST | Invalidate session, clear cookie |
| `/api/auth/me` | GET | Return current user from session or 401 |

**User scoping**
- `UserProfile` carries a `userId` field linking it to the authenticated user
- `DietRecommendationService` resolves the current user from `SecurityContextHolder` on every operation
- `saveProfile` auto-assigns `userId`; `getProfile` verifies ownership; `getAllProfiles` filters by user; `getDietPlans` and `getAllDietPlans` are scoped through profile ownership

**Frontend**
- `AuthContext` provides `{ user, loading, login, register, logout }` via React context; restores session on mount via `/api/auth/me`
- `AuthGuard` redirects unauthenticated users to `/login`
- `AppShell` conditionally renders the Navbar and AuthGuard — the login page gets a clean standalone layout
- Login page with Sign In / Create Account tabs, password visibility toggle, and form validation
- Navbar shows a user avatar with dropdown (username, email, logout); mobile menu includes a logout button
- All API calls include `credentials: "include"` for automatic cookie handling

---

### Application Pages

| Route | Purpose |
|-------|---------|
| `/login` | Login / register page (standalone layout, no navbar) |
| `/` | Dashboard with quick stats and recent activity |
| `/profile` | Profile management — create, edit, list |
| `/recommend` | Generate and review AI diet plans with full audit trail |
| `/outcomes` | Log outcomes, view weight trends, trigger plan adaptation |
| `/plans` | Browse all generated plans |
| `/settings/privacy` | Manage consent, export data, delete data |
| `/admin/fairness` | Run and review fairness audit reports |
| `/admin/benchmarks` | Run and review offline safety benchmarks |
| `/tasks` | Task manager for meal prep and health milestones |

---

### API Surface

**Auth**: `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`

**Profiles**: `GET/POST /api/profiles`, `GET/PUT /api/profiles/:id`

**Diet Plans**: `POST /api/recommend/:profileId`, `GET /api/diet-plans/:profileId`, `GET /api/diet-plans`

**Foods**: `GET /api/foods/search?query=...&culture=...`, `GET /api/foods/:fdcId`, `GET /api/foods/substitutions?fdcId=...&targetCulture=...`, `GET /api/foods/cultures`

**Safety**: `GET /api/safety/alerts/:profileId`, `GET /api/safety/alerts/plan/:planId`

**Audit**: `GET /api/audit/plan/:planId`, `GET /api/audit/profile/:profileId`

**Privacy**: `POST /api/privacy/consent`, `GET /api/privacy/consent/:profileId`, `GET /api/privacy/export/:profileId`, `DELETE /api/privacy/data/:profileId`

**Outcomes**: `POST /api/outcomes`, `GET /api/outcomes/:profileId`, `GET /api/outcomes/trends/:profileId`, `GET /api/outcomes/adapt/:profileId`, `POST /api/plans/:profileId/adapt`

**Admin**: `POST /api/admin/fairness/run`, `GET /api/admin/fairness/reports`, `GET /api/admin/fairness/reports/:id`, `POST /api/admin/benchmarks/run`, `GET /api/admin/outcomes/comparison/:profileId`, `GET/POST /api/admin/sources`, `GET/PUT/DELETE /api/admin/sources/:id`, `POST /api/admin/sources/bulk`, `POST /api/admin/sources/:id/reindex`, `GET /api/admin/sources/stats`

---

### Expert Knowledge Layer (RAG)

DietBuilder now uses a retrieval-augmented generation pipeline so recommendations are grounded in expert evidence, not only model priors.

**Knowledge sources**
- Peer-reviewed and meta-analysis sources are stored in `expert_sources` with title, authors, publication metadata, topics, conditions, and plain-language summaries
- Government and medical guidance (e.g., USDA/WHO/ADA/KDIGO/ACOG-style references) are represented as first-class source records
- Initial source corpus is seeded from `data/expert-sources.json` and can be expanded over time from the admin UI/API

**Retrieval and ranking**
- `EmbeddingService` creates and caches source/query embeddings (`embedding_cache`) using OpenAI embeddings
- `ExpertKnowledgeService` builds profile-aware semantic queries (age, gender, goals, restrictions, medical context, culture)
- Semantic matches are ranked by `SourceRankingService` with weighted credibility, recency, relevance, and profile specificity
- Query result caching avoids repeat retrieval cost for common profile/query shapes

**Grounded plan generation**
- `DietRecommendationService` retrieves and ranks sources before calling the LLM
- `OpenAIService.generateDietPlanWithSources(...)` injects expert evidence and mixed-evidence notes into the generation prompt
- Generated evidence tags are enriched with citation metadata (`sourceId`, DOI/URL, citation text, simple summary)
- Fallback behavior is explicit: when strong sources are missing, evidence is downgraded to lower confidence messaging

**Conflict handling and uncertainty**
- `ConflictResolutionService` groups evidence by topic and detects contradictory findings
- Mixed-evidence topics are persisted as `conflictNotes` on plans with a resolution basis that prioritizes higher-level evidence
- Confidence score + statement are attached to each plan to communicate certainty level

**Provenance and auditability**
- `RecommendationLog` now stores retrieval query, retrieved source IDs, citation counts, and average source relevance
- This creates reproducible, auditable traceability from recommendation output back to source evidence

**Frontend transparency**
- Plan views now include a confidence indicator, citation panel, and mixed-evidence cards
- Users can read plain-language study summaries and open full references
- Admin source management page (`/admin/sources`) supports add/update/deactivate, reindex, and stats

**Continuous improvement**
- `KnowledgeBaseScheduler` verifies and monitors source freshness, clears expired retrieval cache entries, and logs KB health
- Source quality can be improved iteratively by curating/removing low-quality or outdated references

---

### Tech Stack

- **Spring Boot 3.2** with Spring Security (session auth + BCrypt), Spring Data MongoDB, Bean Validation, WebFlux (for OpenAI and USDA API calls)
- **MongoDB** for flexible document storage across core app collections plus `expert_sources` and `embedding_cache`
- **Next.js 16** with App Router and Tailwind CSS v4
- **OpenAI GPT-4o** with JSON mode for structured responses including `MealFood` objects and `EvidenceTag` arrays
- **OpenAI text-embedding-3-small** for semantic retrieval in the RAG knowledge layer
- **USDA FoodData Central API** for nutrient data fallback
- **Lucide React** for icons, **react-hot-toast** for notifications
- **JUnit 5 + Mockito** for offline safety benchmark tests
