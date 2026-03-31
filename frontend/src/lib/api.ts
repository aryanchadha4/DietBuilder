const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";

async function request<T>(
  path: string,
  options?: RequestInit
): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    credentials: "include",
    headers: { "Content-Type": "application/json", ...options?.headers },
    ...options,
  });
  if (!res.ok) {
    const text = await res.text().catch(() => "Unknown error");
    let detail = text;
    try {
      const j = JSON.parse(text) as { message?: string };
      if (j?.message && typeof j.message === "string") {
        detail = j.message;
      }
    } catch {
      /* not JSON */
    }
    throw new Error(`API Error ${res.status}: ${detail}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

// ── Auth Types ──

export interface AuthUser {
  id: string;
  username: string;
  email: string;
  createdAt?: string;
}

// ── Core Types ──

export interface ExerciseSchedule {
  daysPerWeek: number;
  type: string;
  durationMinutes: number;
}

export interface UserProfile {
  id?: string;
  userId?: string;
  name: string;
  age: number;
  gender: string;
  race: string;
  heightCm: number;
  weightKg: number;
  preferredUnits?: "METRIC" | "IMPERIAL";
  strengthTraining: ExerciseSchedule[];
  cardioSchedule: ExerciseSchedule[];
  dietaryRestrictions: string[];
  medicalInfo: string;
  goals: string[];
  availableFoods: string[];
  createdAt?: string;
  updatedAt?: string;
}

export interface Task {
  id?: string;
  title: string;
  description?: string;
  status: "PENDING" | "IN_PROGRESS" | "COMPLETED";
  priority: "LOW" | "MEDIUM" | "HIGH";
  dueDate?: string;
  profileId?: string;
  createdAt?: string;
  updatedAt?: string;
}

// ── Diet Plan Types ──

export interface MealFood {
  fdcId: string | null;
  name: string;
  quantityGrams: number;
  keyNutrients: Record<string, number>;
}

export interface Meal {
  name: string;
  foods: MealFood[];
  calories: number;
  proteinGrams: number;
  carbsGrams: number;
  fatGrams: number;
  fiberGrams?: number;
  micronutrients?: Record<string, number>;
  rationale?: string;
}

export interface MacroBreakdown {
  proteinPercent: number;
  carbsPercent: number;
  fatPercent: number;
}

export interface NutrientStatus {
  planned: number;
  rda: number;
  ul: number;
  unit: string;
  status: "DEFICIENT" | "LOW" | "ADEQUATE" | "EXCESSIVE";
}

export interface NutrientAudit {
  nutrients: Record<string, NutrientStatus>;
  adequacyScore: number;
}

export type EvidenceLevel =
  | "GUIDELINE_BACKED"
  | "META_ANALYSIS"
  | "OBSERVATIONAL"
  | "LOW_CONFIDENCE";

export interface EvidenceTag {
  claim: string;
  level: EvidenceLevel;
  source: string;
  explanation: string;
  sourceId?: string;
  doi?: string;
  url?: string;
  citationText?: string;
  relevanceScore?: number;
  simpleSummary?: string;
}

export interface SafetyAlertData {
  checkType: string;
  severity: "WARNING" | "BLOCK";
  message: string;
  recommendation: string;
}

export interface DayPlan {
  dayNumber: number;
  label: string;
  meals: Meal[];
  dailyCalories: number;
  macroBreakdown: MacroBreakdown;
}

export interface RemovedMealSlot {
  slotId: string;
  dayIndex: number | null;
  originalMealIndex: number;
  mealName?: string;
  removedAt?: string;
}

export interface GroceryList {
  planId: string;
  totalItems: number;
  foods: string[];
}

export interface DietPlan {
  id?: string;
  profileId: string;
  planContent: string;
  meals: Meal[];
  dailyCalories: number;
  macroBreakdown: MacroBreakdown;
  notes: string;

  days?: DayPlan[];
  totalDays?: number;

  nutrientAudit?: NutrientAudit;
  evidenceTags?: EvidenceTag[];
  sourceIds?: string[];
  confidenceStatement?: string;
  evidenceConfidenceScore?: number;
  conflictNotes?: ConflictNote[];
  safetyAlerts?: SafetyAlertData[];
  safetyCleared?: boolean;
  parentPlanId?: string;
  version?: number;

  /** Culture slugs used when this plan was generated (e.g. south-asian). */
  cuisinePreferences?: string[];
  removedMealSlots?: RemovedMealSlot[];

  createdAt?: string;
}

export interface ExpertSource {
  id?: string;
  title: string;
  authors?: string;
  journal?: string;
  organization?: string;
  publicationDate?: string;
  doi?: string;
  url?: string;
  sourceType?: string;
  summary?: string;
  keyFindings?: string[];
  topics?: string[];
  relevantConditions?: string[];
  relevantGoals?: string[];
  ageGroups?: string[];
  genderRelevance?: string[];
  credibilityScore?: number;
  active?: boolean;
}

export interface ConflictNote {
  topic: string;
  summary: string;
  supportingSourceIds: string[];
  opposingSourceIds: string[];
  resolution: string;
  resolutionBasis: string;
}

// ── Food Types ──

export interface FoodItem {
  id?: string;
  fdcId: number;
  description: string;
  foodCategory: string;
  nutrients: Record<string, { amount: number; unit: string }>;
  culturalTags: string[];
  allergenTags: string[];
}

export interface SubstitutionOption {
  fdcId: number;
  name: string;
  servingGrams: number;
  culture: string;
  category: string;
}

// ── Privacy Types ──

export interface ConsentRecord {
  id?: string;
  profileId: string;
  consents: Record<string, boolean>;
  grantedAt?: string;
  revokedAt?: string;
}

// ── Outcome Types ──

export interface OutcomeRecord {
  id?: string;
  profileId: string;
  planId?: string;
  recordedAt?: string;
  weightKg?: number;
  adherencePercent?: number;
  symptoms?: string[];
  labResults?: Record<string, number>;
  trainingResponse?: string;
  notes?: string;
}

export interface OutcomeTrends {
  totalRecords: number;
  weightChangeKg: number;
  avgAdherence: number;
  symptomFrequency: Record<string, number>;
}

export interface AdaptationAssessment {
  adaptRecommended: boolean;
  reasons: string[];
}

// ── Audit Types ──

export interface RecommendationLog {
  id?: string;
  profileId: string;
  planId: string;
  timestamp: string;
  modelName: string;
  systemPrompt?: string;
  userMessage?: string;
  safetyChecksRun: string[];
  postProcessingSteps: string[];
  latencyMs: number;
  retrievedSourceIds?: string[];
  totalSourcesRetrieved?: number;
  totalSourcesCited?: number;
  avgSourceRelevance?: number;
  retrievalQuery?: string;
}

// ── Fairness Types ──

export interface GroupMetrics {
  count: number;
  avgAdequacyScore: number;
  avgCalorieAccuracy: number;
  safetyAlertRate: number;
}

export interface FairnessReport {
  id?: string;
  generatedAt: string;
  byGender: Record<string, GroupMetrics>;
  byAgeGroup: Record<string, GroupMetrics>;
  byCulture: Record<string, GroupMetrics>;
  flaggedDisparities: string[];
  totalPlansAnalyzed: number;
}

export interface BenchmarkCaseResult {
  caseName: string;
  expectedFlags: string[];
  actualFlags: string[];
  passed: boolean;
}

export interface BenchmarkResult {
  totalCases: number;
  passedCases: number;
  passRate: number;
  safetyFlagRecall: number;
  caseResults: BenchmarkCaseResult[];
}

// ── Safety Alert (persisted) ──

export interface SafetyAlert {
  id?: string;
  profileId: string;
  planId: string;
  checkType: string;
  severity: string;
  message: string;
  recommendation: string;
  createdAt?: string;
}

// ── Food Preference Types ──

export interface FoodPreference {
  id?: string;
  userId?: string;
  foodName: string;
  type: "PERMANENT" | "TEMPORARY";
  reason?: string;
  sourcePlanId?: string;
  createdAt?: string;
  expiresAt?: string;
}

export interface SavedMeal {
  id?: string;
  userId?: string;
  profileId?: string;
  sourcePlanId?: string;
  sourceDayIndex?: number | null;
  sourceMealIndex?: number | null;
  meal: Meal;
  tags?: string[];
  createdAt?: string;
}

export interface RegenerateRequest {
  parentPlanId: string;
  rejectedFoods: string[];
  days?: number;
  cuisines?: string[];
}

export interface RegenerateRemovedRequest {
  rejectedFoods?: string[];
}

// ── API Client ──

export const api = {
  auth: {
    register: (username: string, email: string, password: string) =>
      request<AuthUser>("/auth/register", {
        method: "POST",
        body: JSON.stringify({ username, email, password }),
      }),
    login: (username: string, password: string) =>
      request<AuthUser>("/auth/login", {
        method: "POST",
        body: JSON.stringify({ username, password }),
      }),
    logout: () =>
      request<void>("/auth/logout", { method: "POST" }),
    me: () =>
      request<AuthUser>("/auth/me"),
  },

  profiles: {
    list: () => request<UserProfile[]>("/profiles"),
    get: (id: string) => request<UserProfile>(`/profiles/${id}`),
    create: (profile: UserProfile) =>
      request<UserProfile>("/profiles", {
        method: "POST",
        body: JSON.stringify(profile),
      }),
    update: (id: string, profile: Partial<UserProfile>) =>
      request<UserProfile>(`/profiles/${id}`, {
        method: "PUT",
        body: JSON.stringify(profile),
      }),
  },

  tasks: {
    list: () => request<Task[]>("/tasks"),
    get: (id: string) => request<Task>(`/tasks/${id}`),
    create: (task: Partial<Task>) =>
      request<Task>("/tasks", {
        method: "POST",
        body: JSON.stringify(task),
      }),
    update: (id: string, task: Partial<Task>) =>
      request<Task>(`/tasks/${id}`, {
        method: "PUT",
        body: JSON.stringify(task),
      }),
    delete: (id: string) =>
      request<void>(`/tasks/${id}`, { method: "DELETE" }),
  },

  dietPlans: {
    generate: (profileId: string, days: number = 14, cuisines: string[] = []) => {
      const params = new URLSearchParams();
      params.set("days", String(days));
      for (const c of cuisines) params.append("cuisines", c);
      return request<DietPlan>(`/recommend/${profileId}?${params.toString()}`, {
        method: "POST",
      });
    },
    regenerate: (profileId: string, body: RegenerateRequest) =>
      request<DietPlan>(`/recommend/${profileId}/regenerate`, {
        method: "POST",
        body: JSON.stringify(body),
      }),
    listByProfile: (profileId: string) =>
      request<DietPlan[]>(`/diet-plans/${profileId}`),
    listAll: () => request<DietPlan[]>("/diet-plans"),
    removeMeal: (planId: string, mealIndex: number, dayIndex?: number) => {
      const params = new URLSearchParams();
      params.set("mealIndex", String(mealIndex));
      if (dayIndex !== undefined && dayIndex >= 0) {
        params.set("dayIndex", String(dayIndex));
      }
      return request<DietPlan>(
        `/diet-plans/${planId}/meals?${params.toString()}`,
        { method: "DELETE" }
      );
    },
    regenerateRemoved: (planId: string, body: RegenerateRemovedRequest = {}) =>
      request<DietPlan>(`/diet-plans/${planId}/regenerate-removed`, {
        method: "POST",
        body: JSON.stringify(body),
      }),
    groceryList: (planId: string) =>
      request<GroceryList>(`/diet-plans/${planId}/grocery-list`),
  },

  foodPreferences: {
    list: () => request<FoodPreference[]>("/food-preferences"),
    add: (foodName: string, type: "PERMANENT" | "TEMPORARY", reason?: string, sourcePlanId?: string) =>
      request<FoodPreference>("/food-preferences", {
        method: "POST",
        body: JSON.stringify({ foodName, type, reason, sourcePlanId }),
      }),
    remove: (id: string) =>
      request<void>(`/food-preferences/${id}`, { method: "DELETE" }),
    update: (id: string, type: "PERMANENT" | "TEMPORARY") =>
      request<FoodPreference>(`/food-preferences/${id}`, {
        method: "PUT",
        body: JSON.stringify({ type }),
      }),
    resetTemporary: () =>
      request<void>("/food-preferences/reset-temporary", { method: "DELETE" }),
  },

  savedMeals: {
    list: (profileId?: string) =>
      request<SavedMeal[]>(
        `/saved-meals${profileId ? `?profileId=${encodeURIComponent(profileId)}` : ""}`
      ),
    create: (planId: string, mealIndex: number, dayIndex?: number) =>
      request<SavedMeal>("/saved-meals", {
        method: "POST",
        body: JSON.stringify({ planId, mealIndex, dayIndex }),
      }),
    delete: (id: string) =>
      request<void>(`/saved-meals/${id}`, { method: "DELETE" }),
    insert: (savedMealId: string, planId: string, mealIndex: number, dayIndex?: number) =>
      request<DietPlan>(`/saved-meals/${savedMealId}/insert`, {
        method: "POST",
        body: JSON.stringify({ planId, mealIndex, dayIndex }),
      }),
  },

  foods: {
    search: (q: string, culture?: string) =>
      request<FoodItem[]>(`/foods/search?query=${encodeURIComponent(q)}${culture ? `&culture=${culture}` : ""}`),
    get: (fdcId: number) => request<FoodItem>(`/foods/${fdcId}`),
    substitutions: (fdcId: number, culture: string) =>
      request<SubstitutionOption[]>(`/foods/substitutions?fdcId=${fdcId}&culture=${encodeURIComponent(culture)}`),
    cultures: () => request<string[]>("/foods/cultures"),
  },

  safety: {
    alertsForProfile: (profileId: string) =>
      request<SafetyAlert[]>(`/safety/alerts/${profileId}`),
    alertsForPlan: (planId: string) =>
      request<SafetyAlert[]>(`/safety/alerts/plan/${planId}`),
  },

  audit: {
    forPlan: (planId: string) =>
      request<RecommendationLog>(`/audit/plan/${planId}`),
    forProfile: (profileId: string) =>
      request<RecommendationLog[]>(`/audit/profile/${profileId}`),
  },

  privacy: {
    getConsent: (profileId: string) =>
      request<ConsentRecord>(`/privacy/consent/${profileId}`),
    setConsent: (profileId: string, consents: Record<string, boolean>) =>
      request<ConsentRecord>("/privacy/consent", {
        method: "POST",
        body: JSON.stringify({ profileId, consents }),
      }),
    exportData: (profileId: string) =>
      request<Record<string, unknown>>(`/privacy/export/${profileId}`),
    deleteData: (profileId: string) =>
      request<{ status: string }>(`/privacy/data/${profileId}`, { method: "DELETE" }),
  },

  outcomes: {
    record: (outcome: Partial<OutcomeRecord>) =>
      request<OutcomeRecord>("/outcomes", {
        method: "POST",
        body: JSON.stringify(outcome),
      }),
    list: (profileId: string) =>
      request<OutcomeRecord[]>(`/outcomes/${profileId}`),
    trends: (profileId: string) =>
      request<OutcomeTrends>(`/outcomes/trends/${profileId}`),
    checkAdaptation: (profileId: string) =>
      request<AdaptationAssessment>(`/outcomes/adapt/${profileId}`),
    adaptPlan: (profileId: string) =>
      request<DietPlan>(`/plans/${profileId}/adapt`, { method: "POST" }),
  },

  admin: {
    runFairness: () =>
      request<FairnessReport>("/admin/fairness/run", { method: "POST" }),
    fairnessReports: () =>
      request<FairnessReport[]>("/admin/fairness/reports"),
    fairnessReport: (id: string) =>
      request<FairnessReport>(`/admin/fairness/reports/${id}`),
    runBenchmarks: () =>
      request<BenchmarkResult>("/admin/benchmarks/run", { method: "POST" }),
    outcomeComparison: (profileId: string) =>
      request<Record<string, unknown>>(`/admin/outcomes/comparison/${profileId}`),
    sources: (params?: { sourceType?: string; active?: boolean }) => {
      const qs = new URLSearchParams();
      if (params?.sourceType) qs.set("sourceType", params.sourceType);
      if (params?.active !== undefined) qs.set("active", String(params.active));
      return request<ExpertSource[]>(`/admin/sources${qs.toString() ? `?${qs.toString()}` : ""}`);
    },
    sourceDetail: (id: string) =>
      request<ExpertSource>(`/admin/sources/${id}`),
    addSource: (source: Partial<ExpertSource>) =>
      request<ExpertSource>("/admin/sources", {
        method: "POST",
        body: JSON.stringify(source),
      }),
    updateSource: (id: string, source: Partial<ExpertSource>) =>
      request<ExpertSource>(`/admin/sources/${id}`, {
        method: "PUT",
        body: JSON.stringify(source),
      }),
    deleteSource: (id: string) =>
      request<void>(`/admin/sources/${id}`, { method: "DELETE" }),
    bulkSources: (sources: Partial<ExpertSource>[]) =>
      request<ExpertSource[]>("/admin/sources/bulk", {
        method: "POST",
        body: JSON.stringify(sources),
      }),
    reindexSource: (id: string) =>
      request<ExpertSource>(`/admin/sources/${id}/reindex`, { method: "POST" }),
    sourceStats: () =>
      request<Record<string, unknown>>("/admin/sources/stats"),
  },
};
