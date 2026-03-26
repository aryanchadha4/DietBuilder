"use client";

import { useEffect, useState, Suspense } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import {
  api,
  UserProfile,
  DietPlan,
  FoodPreference,
  RecommendationLog,
} from "@/lib/api";
import { formatWeight } from "@/lib/units";
import { MultiDayPlanView } from "@/components/MultiDayPlanView";
import { AuditLogViewer } from "@/components/AuditLogViewer";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { CardSkeleton } from "@/components/ui/Skeleton";
import { EmptyState } from "@/components/ui/EmptyState";
import toast from "react-hot-toast";
import {
  Sparkles,
  History,
  ChevronDown,
  ChevronUp,
  ShieldBan,
  RefreshCw,
  CalendarDays,
} from "lucide-react";

type CuisineChip = { value: string; label: string };

const CUISINE_ROWS: { title: string; items: CuisineChip[] }[] = [
  {
    title: "Asia",
    items: [
      { value: "south-asian", label: "South Asian" },
      { value: "east-asian", label: "East Asian" },
      { value: "southeast-asian", label: "Southeast Asian" },
    ],
  },
  {
    title: "Americas & Mediterranean",
    items: [
      { value: "latin-american", label: "Latin American" },
      { value: "caribbean", label: "Caribbean" },
      { value: "mediterranean", label: "Mediterranean" },
      { value: "middle-eastern", label: "Middle Eastern" },
    ],
  },
  {
    title: "Africa & Europe",
    items: [
      { value: "west-african", label: "West African" },
      { value: "east-african", label: "East African" },
      { value: "northern-european", label: "Northern European" },
    ],
  },
];

const LOADING_MESSAGES = [
  "Running pre-generation safety screening...",
  "Computing your BMR and TDEE targets...",
  "Generating personalized multi-day meal plan with AI...",
  "Building varied daily menus across all days...",
  "Auditing nutrient adequacy against DRI/UL...",
  "Running post-generation safety guardrails...",
  "Tagging evidence levels on recommendations...",
  "Logging audit trail...",
  "Almost there...",
];

function RecommendContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const profileId = searchParams.get("profileId");

  const [profiles, setProfiles] = useState<UserProfile[]>([]);
  const [selectedProfileId, setSelectedProfileId] = useState<string>(
    profileId || ""
  );
  const [numDays, setNumDays] = useState(14);
  const [planMode, setPlanMode] = useState<"monolith" | "hybrid">("hybrid");
  const [hybridDepth, setHybridDepth] = useState<
    "fast" | "detailed" | "expert"
  >("detailed");
  /** First response only includes this many days when set (hybrid progressive). */
  const [syncDays, setSyncDays] = useState<number | "">("");
  const [selectedCuisines, setSelectedCuisines] = useState<string[]>([]);
  const [currentPlan, setCurrentPlan] = useState<DietPlan | null>(null);
  const [auditLog, setAuditLog] = useState<RecommendationLog | null>(null);
  const [pastPlans, setPastPlans] = useState<DietPlan[]>([]);
  const [foodPrefs, setFoodPrefs] = useState<FoodPreference[]>([]);
  const [rejectedThisSession, setRejectedThisSession] = useState<string[]>([]);
  const [generating, setGenerating] = useState(false);
  const [regenerating, setRegenerating] = useState(false);
  const [completingDays, setCompletingDays] = useState(false);
  const [loadingMsg, setLoadingMsg] = useState(0);
  const [loading, setLoading] = useState(true);
  const [showHistory, setShowHistory] = useState(false);

  useEffect(() => {
    Promise.all([
      api.profiles.list(),
      api.foodPreferences.list().catch(() => [] as FoodPreference[]),
    ])
      .then(([p, prefs]) => {
        setProfiles(p);
        setFoodPrefs(prefs);
        if (!selectedProfileId && p.length > 0) {
          setSelectedProfileId(p[0].id!);
        }
      })
      .catch(() => toast.error("Failed to load profiles"))
      .finally(() => setLoading(false));
  }, [selectedProfileId]);

  useEffect(() => {
    if (selectedProfileId) {
      api.dietPlans
        .listByProfile(selectedProfileId)
        .then(setPastPlans)
        .catch(() => {});
    }
  }, [selectedProfileId, currentPlan]);

  useEffect(() => {
    setSelectedCuisines([]);
  }, [selectedProfileId]);

  useEffect(() => {
    if (!generating && !regenerating && !completingDays) return;
    const interval = setInterval(() => {
      setLoadingMsg((prev) => (prev + 1) % LOADING_MESSAGES.length);
    }, 3500);
    return () => clearInterval(interval);
  }, [generating, regenerating, completingDays]);

  async function handleGenerate() {
    if (!selectedProfileId) {
      toast.error("Please select a profile first");
      return;
    }
    setGenerating(true);
    setLoadingMsg(0);
    setAuditLog(null);
    setRejectedThisSession([]);
    try {
      const plan = await api.dietPlans.generate(
        selectedProfileId,
        numDays,
        selectedCuisines,
        {
          planMode,
          hybridDepth,
          syncDays:
            typeof syncDays === "number" && syncDays > 0
              ? Math.min(syncDays, numDays)
              : undefined,
        }
      );
      setCurrentPlan(plan);

      if (
        plan.safetyAlerts &&
        plan.safetyAlerts.some((a) => a.severity === "BLOCK")
      ) {
        toast.error("Plan generation blocked due to safety concerns");
      } else if (plan.safetyAlerts && plan.safetyAlerts.length > 0) {
        toast("Plan generated with safety warnings", { icon: "⚠️" });
      } else {
        toast.success(
          `${plan.totalDays || 1}-day diet plan generated!`
        );
      }

      if (plan.id) {
        api.audit.forPlan(plan.id).then(setAuditLog).catch(() => {});
      }
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : "Failed to generate plan";
      if (msg.includes("consent")) {
        toast.error(
          "Please grant AI processing consent in Privacy Settings first."
        );
      } else {
        toast.error(msg);
      }
    } finally {
      setGenerating(false);
    }
  }

  async function handleCompleteRemainingDays() {
    if (!currentPlan?.id) return;
    const total = currentPlan.totalDays ?? 0;
    const have = currentPlan.days?.length ?? 0;
    if (total <= 0 || have >= total) return;
    setCompletingDays(true);
    setLoadingMsg(0);
    try {
      const next = await api.dietPlans.completeRemainingDays(currentPlan.id);
      setCurrentPlan(next);
      setPastPlans((prev) =>
        prev.map((p) => (p.id === next.id ? next : p))
      );
      toast.success(
        `Extended plan: ${next.days?.length ?? 0} of ${next.totalDays ?? "?"} days`
      );
    } catch (e: unknown) {
      const msg =
        e instanceof Error ? e.message : "Failed to complete remaining days";
      toast.error(msg);
    } finally {
      setCompletingDays(false);
    }
  }

  async function handleRejectFood(
    foodName: string,
    type: "PERMANENT" | "TEMPORARY",
    reason?: string,
    planId?: string
  ) {
    try {
      const pref = await api.foodPreferences.add(
        foodName,
        type,
        reason,
        planId
      );
      setFoodPrefs((prev) => [...prev.filter((p) => p.foodName !== foodName), pref]);
      setRejectedThisSession((prev) =>
        prev.includes(foodName) ? prev : [...prev, foodName]
      );
      toast.success(
        `"${foodName}" excluded (${type === "PERMANENT" ? "permanently" : "30 days"})`
      );
    } catch {
      toast.error("Failed to save food preference");
    }
  }

  async function handleRegenerate() {
    if (!selectedProfileId || !currentPlan?.id || rejectedThisSession.length === 0)
      return;

    setRegenerating(true);
    setLoadingMsg(0);
    setAuditLog(null);
    try {
      const plan = await api.dietPlans.regenerate(selectedProfileId, {
        parentPlanId: currentPlan.id,
        rejectedFoods: rejectedThisSession,
        days: numDays,
        cuisines: selectedCuisines,
      });
      setCurrentPlan(plan);
      setRejectedThisSession([]);

      toast.success(
        `Plan regenerated (v${plan.version || 2}) without rejected foods`
      );

      if (plan.id) {
        api.audit.forPlan(plan.id).then(setAuditLog).catch(() => {});
      }
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : "Failed to regenerate plan";
      toast.error(msg);
    } finally {
      setRegenerating(false);
    }
  }

  async function handleRemoveMeal({
    dayIndex,
    mealIndex,
  }: {
    dayIndex: number | null;
    mealIndex: number;
  }) {
    if (!currentPlan?.id) return;
    try {
      const next = await api.dietPlans.removeMeal(
        currentPlan.id,
        mealIndex,
        dayIndex ?? undefined
      );
      setCurrentPlan(next);
      setPastPlans((prev) =>
        prev.map((p) => (p.id === next.id ? next : p))
      );
      const pendingRemoved = next.removedMealSlots?.length ?? 0;
      toast.success(
        pendingRemoved > 0
          ? `Meal removed. ${pendingRemoved} replacement${pendingRemoved !== 1 ? "s" : ""} pending.`
          : "Meal removed from plan"
      );
    } catch {
      toast.error("Failed to remove meal");
    }
  }

  async function handleReplaceRemovedMeals() {
    if (!currentPlan?.id) return;
    const pendingRemoved = currentPlan.removedMealSlots?.length ?? 0;
    if (pendingRemoved === 0) return;
    setRegenerating(true);
    setLoadingMsg(0);
    setAuditLog(null);
    try {
      const next = await api.dietPlans.regenerateRemoved(currentPlan.id, {
        rejectedFoods: rejectedThisSession,
      });
      setCurrentPlan(next);
      setPastPlans((prev) => prev.map((p) => (p.id === next.id ? next : p)));
      toast.success(
        `Replaced ${pendingRemoved} removed meal${pendingRemoved !== 1 ? "s" : ""}`
      );
      if (next.id) {
        api.audit.forPlan(next.id).then(setAuditLog).catch(() => {});
      }
    } catch (e: unknown) {
      const msg =
        e instanceof Error ? e.message : "Failed to replace removed meals";
      toast.error(msg);
    } finally {
      setRegenerating(false);
    }
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6 space-y-4">
        <CardSkeleton />
        <CardSkeleton />
      </div>
    );
  }

  if (profiles.length === 0) {
    return (
      <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6">
        <EmptyState
          icon={Sparkles}
          title="No profiles found"
          description="Create a health profile first to get personalized diet recommendations."
          actionLabel="Create Profile"
          onAction={() => router.push("/profile")}
        />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6">
      <div className="mb-8">
        <h1 className="text-2xl font-bold">Diet Recommendation</h1>
        <p className="text-sm text-muted-foreground mt-1">
          AI-powered multi-day dietary planning with nutrient auditing, safety
          guardrails, and evidence grading
        </p>
      </div>

      <Card className="mb-8">
        <div className="flex flex-col gap-4">
          <div className="flex flex-col sm:flex-row items-start sm:items-end gap-4">
            <div className="flex-1 w-full">
              <label className="block text-sm font-medium mb-1.5">
                Select Profile
              </label>
              <select
                value={selectedProfileId}
                onChange={(e) => setSelectedProfileId(e.target.value)}
                className="w-full rounded-xl border border-border bg-card px-4 py-2.5 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-ring/20"
              >
                {profiles.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} -- {p.age}y,{" "}
                    {formatWeight(p.weightKg, p.preferredUnits)}
                  </option>
                ))}
              </select>
            </div>

            <div className="w-full sm:w-40">
              <label className="block text-sm font-medium mb-1.5">
                <CalendarDays className="h-3.5 w-3.5 inline mr-1" />
                Plan Days
              </label>
              <select
                value={numDays}
                onChange={(e) => setNumDays(Number(e.target.value))}
                className="w-full rounded-xl border border-border bg-card px-4 py-2.5 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-ring/20"
              >
                {[7, 10, 12, 14, 15].map((n) => (
                  <option key={n} value={n}>
                    {n} days
                  </option>
                ))}
              </select>
            </div>

            <div className="w-full sm:w-44">
              <label className="block text-sm font-medium mb-1.5">
                Generation
              </label>
              <select
                value={planMode}
                onChange={(e) =>
                  setPlanMode(e.target.value as "monolith" | "hybrid")
                }
                className="w-full rounded-xl border border-border bg-card px-4 py-2.5 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-ring/20"
              >
                <option value="hybrid">Hybrid (fast)</option>
                <option value="monolith">Full AI (slow)</option>
              </select>
            </div>

            {planMode === "hybrid" && (
              <div className="w-full sm:w-44">
                <label className="block text-sm font-medium mb-1.5">Depth</label>
                <select
                  value={hybridDepth}
                  onChange={(e) =>
                    setHybridDepth(
                      e.target.value as "fast" | "detailed" | "expert"
                    )
                  }
                  className="w-full rounded-xl border border-border bg-card px-4 py-2.5 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-ring/20"
                >
                  <option value="fast">Fast (no polish)</option>
                  <option value="detailed">Detailed (+ titles)</option>
                  <option value="expert">Expert (+ evidence)</option>
                </select>
              </div>
            )}

            {planMode === "hybrid" && (
              <div className="w-full sm:w-36">
                <label className="block text-sm font-medium mb-1.5">
                  Sync days
                </label>
                <input
                  type="number"
                  min={1}
                  max={numDays}
                  placeholder="All"
                  value={syncDays}
                  onChange={(e) => {
                    const v = e.target.value;
                    if (v === "") setSyncDays("");
                    else setSyncDays(Math.min(numDays, Math.max(1, Number(v))));
                  }}
                  className="w-full rounded-xl border border-border bg-card px-4 py-2.5 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-ring/20"
                />
              </div>
            )}

            <Button
              onClick={handleGenerate}
              loading={generating}
              size="lg"
            >
              <Sparkles className="h-4 w-4" />
              Generate Plan
            </Button>
          </div>

          <div className="pt-1 border-t border-border/80">
            <label className="block text-sm font-medium mb-1.5">
              Cuisines to emphasize
            </label>
            <p className="text-xs text-muted-foreground mb-4 max-w-2xl">
              Tap chips to select traditions for this plan. Leave none selected for no cuisine
              constraint.
            </p>
            <div className="space-y-5">
              {CUISINE_ROWS.map((row) => (
                <div key={row.title}>
                  <p className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground mb-2.5 pl-0.5">
                    {row.title}
                  </p>
                  <div className="flex flex-wrap gap-2 sm:gap-2.5">
                    {row.items.map(({ value, label }) => {
                      const selected = selectedCuisines.includes(value);
                      return (
                        <button
                          key={value}
                          type="button"
                          aria-pressed={selected}
                          onClick={() => {
                            setSelectedCuisines((prev) =>
                              prev.includes(value)
                                ? prev.filter((c) => c !== value)
                                : [...prev, value]
                            );
                          }}
                          className={[
                            "rounded-full px-3.5 py-2 text-sm font-medium transition-all duration-200",
                            "border focus:outline-none focus-visible:ring-2 focus-visible:ring-primary/35 focus-visible:ring-offset-2 focus-visible:ring-offset-background",
                            selected
                              ? "border-primary bg-primary text-primary-foreground shadow-sm scale-[1.02]"
                              : "border-border bg-card text-muted-foreground hover:border-primary/40 hover:bg-primary/5 hover:text-foreground active:scale-[0.98]",
                          ].join(" ")}
                        >
                          {label}
                        </button>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Disliked foods indicator */}
          {foodPrefs.length > 0 && (
            <div className="flex items-center gap-2 text-xs text-muted-foreground border-t border-border pt-3">
              <ShieldBan className="h-3.5 w-3.5" />
              <span>
                {foodPrefs.length} excluded food
                {foodPrefs.length !== 1 ? "s" : ""} will be automatically
                avoided
              </span>
              <div className="flex flex-wrap gap-1 ml-1">
                {foodPrefs.slice(0, 5).map((p) => (
                  <Badge key={p.id} variant={p.type === "PERMANENT" ? "danger" : "warning"}>
                    {p.foodName}
                  </Badge>
                ))}
                {foodPrefs.length > 5 && (
                  <Badge>+{foodPrefs.length - 5} more</Badge>
                )}
              </div>
            </div>
          )}
        </div>
      </Card>

      {/* Loading state */}
      {(generating || regenerating || completingDays) && (
        <div className="flex flex-col items-center justify-center py-16 animate-fade-in">
          <div className="relative mb-6">
            <div className="h-16 w-16 rounded-full border-4 border-primary/20 border-t-primary animate-spin" />
          </div>
          <p className="text-sm font-medium text-foreground animate-pulse-slow">
            {LOADING_MESSAGES[loadingMsg]}
          </p>
          <p className="text-xs text-muted-foreground mt-2">
            {regenerating
              ? "Refreshing plan changes..."
              : completingDays
                ? "Loading remaining days..."
                : `Generating ${numDays}-day plan. This may take 30-60 seconds.`}
          </p>
          <div className="mt-6 space-y-1.5">
            {LOADING_MESSAGES.slice(0, -1).map((msg, i) => (
              <div
                key={i}
                className={`flex items-center gap-2 text-xs ${i <= loadingMsg ? "text-primary" : "text-muted-foreground/40"}`}
              >
                <div
                  className={`h-1.5 w-1.5 rounded-full ${i < loadingMsg ? "bg-primary" : i === loadingMsg ? "bg-primary animate-pulse" : "bg-muted"}`}
                />
                {msg}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Regeneration bar */}
      {!generating &&
        !regenerating &&
        currentPlan &&
        rejectedThisSession.length > 0 && (
          <div className="mb-6 rounded-xl border border-amber-200 bg-amber-50/50 dark:border-amber-800 dark:bg-amber-900/10 p-4 flex items-center justify-between animate-fade-in">
            <div className="flex items-center gap-2">
              <ShieldBan className="h-5 w-5 text-amber-600" />
              <div>
                <p className="text-sm font-medium text-amber-800 dark:text-amber-300">
                  {rejectedThisSession.length} food
                  {rejectedThisSession.length !== 1 ? "s" : ""} rejected
                </p>
                <p className="text-xs text-amber-600 dark:text-amber-400">
                  {rejectedThisSession.join(", ")}
                </p>
              </div>
            </div>
            <Button onClick={handleRegenerate} size="sm">
              <RefreshCw className="h-3.5 w-3.5" />
              Regenerate Plan
            </Button>
          </div>
        )}

      {/* Current plan */}
      {!generating && !regenerating && currentPlan && (
        <div className="space-y-6">
          {currentPlan.totalDays != null &&
            currentPlan.days != null &&
            currentPlan.days.length < currentPlan.totalDays && (
              <div className="rounded-xl border border-primary/25 bg-primary/5 px-4 py-3 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                <p className="text-sm text-foreground">
                  Showing {currentPlan.days.length} of {currentPlan.totalDays}{" "}
                  days (progressive hybrid). Load the rest when ready.
                </p>
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={handleCompleteRemainingDays}
                  loading={completingDays}
                  disabled={completingDays}
                >
                  Load remaining days
                </Button>
              </div>
            )}
          <MultiDayPlanView
            plan={currentPlan}
            onRejectFood={handleRejectFood}
            onRemoveMeal={handleRemoveMeal}
            onReplaceRemovedMeals={handleReplaceRemovedMeals}
            replaceRemovedLoading={regenerating}
          />
          <AuditLogViewer log={auditLog} />
        </div>
      )}

      {/* Past plans */}
      {!generating && !regenerating && pastPlans.length > 0 && (
        <div className="mt-8">
          <button
            onClick={() => setShowHistory(!showHistory)}
            className="flex items-center gap-2 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors mb-4"
          >
            <History className="h-4 w-4" />
            Previous Plans ({pastPlans.length})
            {showHistory ? (
              <ChevronUp className="h-4 w-4" />
            ) : (
              <ChevronDown className="h-4 w-4" />
            )}
          </button>
          {showHistory && (
            <div className="space-y-4">
              {pastPlans.map((plan) => (
                <Card
                  key={plan.id}
                  hover
                  className="cursor-pointer"
                  onClick={() => {
                    setCurrentPlan(plan);
                    setRejectedThisSession([]);
                    if (plan.id)
                      api.audit
                        .forPlan(plan.id)
                        .then(setAuditLog)
                        .catch(() => {});
                  }}
                >
                  <div className="flex items-center justify-between">
                    <div>
                      <div className="flex items-center gap-2">
                        <p className="font-medium">
                          {plan.dailyCalories} kcal/day
                        </p>
                        {plan.totalDays && plan.totalDays > 0 && (
                          <Badge variant="info">
                            {plan.totalDays} days
                          </Badge>
                        )}
                        {plan.safetyCleared === false && (
                          <span className="text-xs text-red-500 font-medium">
                            Safety issues
                          </span>
                        )}
                        {plan.version && plan.version > 1 && (
                          <span className="text-xs text-primary font-medium">
                            v{plan.version}
                          </span>
                        )}
                        {plan.nutrientAudit && (
                          <span className="text-xs text-muted-foreground">
                            Adequacy: {plan.nutrientAudit.adequacyScore}%
                          </span>
                        )}
                      </div>
                      <p className="text-xs text-muted-foreground">
                        {plan.meals?.length || 0} meals &middot;{" "}
                        {plan.createdAt
                          ? new Date(plan.createdAt).toLocaleDateString()
                          : "Unknown date"}
                      </p>
                    </div>
                    <span className="text-xs text-primary font-medium">
                      View
                    </span>
                  </div>
                </Card>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default function RecommendPage() {
  return (
    <Suspense
      fallback={
        <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6 space-y-4">
          <CardSkeleton />
          <CardSkeleton />
        </div>
      }
    >
      <RecommendContent />
    </Suspense>
  );
}
