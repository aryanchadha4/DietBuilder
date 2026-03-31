"use client";

import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { api, UserProfile, DietPlan } from "@/lib/api";
import { MultiDayPlanView } from "@/components/MultiDayPlanView";
import { Card } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { CardSkeleton } from "@/components/ui/Skeleton";
import { EmptyState } from "@/components/ui/EmptyState";
import toast from "react-hot-toast";
import {
  Sparkles,
  ChevronLeft,
  Flame,
  Calendar,
  User,
  Filter,
  ShieldBan,
  RefreshCw,
} from "lucide-react";
import { Suspense } from "react";

function PlansContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const filterProfileId = searchParams.get("profileId");

  const [profiles, setProfiles] = useState<UserProfile[]>([]);
  const [plans, setPlans] = useState<DietPlan[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedPlan, setSelectedPlan] = useState<DietPlan | null>(null);
  const [selectedProfileFilter, setSelectedProfileFilter] = useState<string>(
    filterProfileId || "all"
  );
  const [rejectedThisSession, setRejectedThisSession] = useState<string[]>(
    []
  );
  const [regenerating, setRegenerating] = useState(false);

  useEffect(() => {
    Promise.all([
      api.profiles.list().catch(() => [] as UserProfile[]),
      api.dietPlans.listAll().catch(() => [] as DietPlan[]),
    ])
      .then(([p, d]) => {
        setProfiles(p);
        setPlans(d);
      })
      .catch(() => toast.error("Failed to load data"))
      .finally(() => setLoading(false));
  }, []);

  const profileMap = new Map(profiles.map((p) => [p.id, p]));

  const filteredPlans =
    selectedProfileFilter === "all"
      ? plans
      : plans.filter((p) => p.profileId === selectedProfileFilter);

  const sortedPlans = [...filteredPlans].sort(
    (a, b) =>
      new Date(b.createdAt || 0).getTime() -
      new Date(a.createdAt || 0).getTime()
  );

  async function handleRejectFood(
    foodName: string,
    type: "PERMANENT" | "TEMPORARY",
    reason?: string,
    planId?: string
  ) {
    try {
      await api.foodPreferences.add(foodName, type, reason, planId);
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
    if (
      !selectedPlan?.id ||
      !selectedPlan.profileId ||
      rejectedThisSession.length === 0
    )
      return;

    const days =
      selectedPlan.totalDays && selectedPlan.totalDays > 0
        ? selectedPlan.totalDays
        : 14;

    setRegenerating(true);
    try {
      const plan = await api.dietPlans.regenerate(selectedPlan.profileId, {
        parentPlanId: selectedPlan.id,
        rejectedFoods: rejectedThisSession,
        days,
        cuisines: selectedPlan.cuisinePreferences,
      });
      setSelectedPlan(plan);
      setRejectedThisSession([]);
      setPlans((prev) => {
        const without = prev.filter((p) => p.id !== plan.id);
        return [plan, ...without];
      });
      toast.success(
        `Plan regenerated (v${plan.version || 2}) without rejected foods`
      );
    } catch (e: unknown) {
      toast.error(
        e instanceof Error ? e.message : "Failed to regenerate plan"
      );
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
    if (!selectedPlan?.id) return;
    try {
      const next = await api.dietPlans.removeMeal(
        selectedPlan.id,
        mealIndex,
        dayIndex ?? undefined
      );
      setSelectedPlan(next);
      setPlans((prev) => prev.map((p) => (p.id === next.id ? next : p)));
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
    if (!selectedPlan?.id) return;
    const pendingRemoved = selectedPlan.removedMealSlots?.length ?? 0;
    if (pendingRemoved === 0) return;
    setRegenerating(true);
    try {
      const next = await api.dietPlans.regenerateRemoved(selectedPlan.id, {
        rejectedFoods: rejectedThisSession,
      });
      setSelectedPlan(next);
      setPlans((prev) => prev.map((p) => (p.id === next.id ? next : p)));
      toast.success(
        `Replaced ${pendingRemoved} removed meal${pendingRemoved !== 1 ? "s" : ""}`
      );
    } catch (e: unknown) {
      toast.error(
        e instanceof Error ? e.message : "Failed to replace removed meals"
      );
    } finally {
      setRegenerating(false);
    }
  }

  async function handleSaveMeal({
    dayIndex,
    mealIndex,
  }: {
    dayIndex: number | null;
    mealIndex: number;
  }) {
    if (!selectedPlan?.id) return;
    try {
      await api.savedMeals.create(
        selectedPlan.id,
        mealIndex,
        dayIndex ?? undefined
      );
      toast.success("Meal saved");
    } catch {
      toast.error("Failed to save meal");
    }
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6 space-y-4">
        <CardSkeleton />
        <CardSkeleton />
        <CardSkeleton />
      </div>
    );
  }

  if (selectedPlan) {
    const profile = profileMap.get(selectedPlan.profileId);
    return (
      <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6">
        <button
          onClick={() => {
            setSelectedPlan(null);
            setRejectedThisSession([]);
          }}
          className="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors mb-6"
        >
          <ChevronLeft className="h-4 w-4" />
          Back to all plans
        </button>
        {profile && (
          <div className="mb-4">
            <p className="text-sm text-muted-foreground">
              Plan for{" "}
              <span className="font-medium text-foreground">
                {profile.name}
              </span>{" "}
              &middot;{" "}
              {selectedPlan.createdAt
                ? new Date(selectedPlan.createdAt).toLocaleDateString(
                    undefined,
                    { year: "numeric", month: "long", day: "numeric" }
                  )
                : "Unknown date"}
            </p>
          </div>
        )}
        {rejectedThisSession.length > 0 && (
          <div className="mb-6 rounded-xl border border-amber-200 bg-amber-50/50 dark:border-amber-800 dark:bg-amber-900/10 p-4 flex items-center justify-between animate-fade-in">
            <div className="flex items-center gap-2 min-w-0">
              <ShieldBan className="h-5 w-5 text-amber-600 shrink-0" />
              <div className="min-w-0">
                <p className="text-sm font-medium text-amber-800 dark:text-amber-300">
                  {rejectedThisSession.length} food
                  {rejectedThisSession.length !== 1 ? "s" : ""} rejected
                </p>
                <p className="text-xs text-amber-600 dark:text-amber-400 truncate">
                  {rejectedThisSession.join(", ")}
                </p>
              </div>
            </div>
            <Button
              onClick={handleRegenerate}
              loading={regenerating}
              size="sm"
              className="shrink-0"
            >
              <RefreshCw className="h-3.5 w-3.5" />
              Regenerate Plan
            </Button>
          </div>
        )}
        <MultiDayPlanView
          plan={selectedPlan}
          onRejectFood={handleRejectFood}
          onRemoveMeal={handleRemoveMeal}
          onReplaceRemovedMeals={handleReplaceRemovedMeals}
          replaceRemovedLoading={regenerating}
          onSaveMeal={handleSaveMeal}
        />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold">Diet Plans</h1>
          <p className="text-sm text-muted-foreground mt-1">
            Browse all your generated diet plans
          </p>
        </div>
        <button
          onClick={() => router.push("/recommend")}
          className="inline-flex items-center gap-2 rounded-xl bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground hover:bg-primary-dark transition-colors"
        >
          <Sparkles className="h-4 w-4" />
          New Plan
        </button>
      </div>

      {/* Profile filter */}
      {profiles.length > 1 && (
        <div className="mb-6">
          <div className="flex items-center gap-2 mb-2">
            <Filter className="h-4 w-4 text-muted-foreground" />
            <span className="text-sm font-medium text-muted-foreground">
              Filter by profile
            </span>
          </div>
          <div className="flex flex-wrap gap-2">
            <button
              onClick={() => setSelectedProfileFilter("all")}
              className={`rounded-full px-3 py-1.5 text-sm font-medium transition-colors ${
                selectedProfileFilter === "all"
                  ? "bg-primary text-primary-foreground"
                  : "bg-secondary text-secondary-foreground hover:bg-secondary/80"
              }`}
            >
              All ({plans.length})
            </button>
            {profiles.map((profile) => {
              const count = plans.filter(
                (p) => p.profileId === profile.id
              ).length;
              if (count === 0) return null;
              return (
                <button
                  key={profile.id}
                  onClick={() =>
                    setSelectedProfileFilter(profile.id!)
                  }
                  className={`rounded-full px-3 py-1.5 text-sm font-medium transition-colors ${
                    selectedProfileFilter === profile.id
                      ? "bg-primary text-primary-foreground"
                      : "bg-secondary text-secondary-foreground hover:bg-secondary/80"
                  }`}
                >
                  {profile.name} ({count})
                </button>
              );
            })}
          </div>
        </div>
      )}

      {sortedPlans.length === 0 ? (
        <EmptyState
          icon={Sparkles}
          title="No diet plans yet"
          description="Generate your first AI-powered diet plan based on your health profile."
          actionLabel="Generate a Plan"
          onAction={() => router.push("/recommend")}
        />
      ) : (
        <div className="space-y-3">
          {sortedPlans.map((plan) => {
            const profile = profileMap.get(plan.profileId);
            return (
              <Card
                key={plan.id}
                hover
                className="cursor-pointer"
                onClick={() => setSelectedPlan(plan)}
              >
                <div className="flex items-center justify-between">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <Flame className="h-4 w-4 text-orange-500 shrink-0" />
                      <p className="font-semibold">
                        {plan.dailyCalories} kcal/day
                      </p>
                      {plan.totalDays && plan.totalDays > 0 && (
                        <Badge variant="info">{plan.totalDays} days</Badge>
                      )}
                      {plan.version && plan.version > 1 && (
                        <Badge>v{plan.version}</Badge>
                      )}
                    </div>
                    <div className="flex items-center gap-3 text-xs text-muted-foreground">
                      {profile && (
                        <span className="flex items-center gap-1">
                          <User className="h-3 w-3" />
                          {profile.name}
                        </span>
                      )}
                      <span>
                        {plan.totalDays && plan.totalDays > 0
                          ? `${plan.totalDays} days`
                          : `${plan.meals?.length || 0} meals`}
                      </span>
                      {plan.createdAt && (
                        <span className="flex items-center gap-1">
                          <Calendar className="h-3 w-3" />
                          {new Date(plan.createdAt).toLocaleDateString()}
                        </span>
                      )}
                    </div>
                  </div>
                  <div className="flex items-center gap-2 shrink-0 ml-4">
                    {plan.macroBreakdown && (
                      <div className="hidden sm:flex gap-1.5">
                        <Badge variant="success">
                          P {Math.round(plan.macroBreakdown.proteinPercent)}%
                        </Badge>
                        <Badge variant="warning">
                          C {Math.round(plan.macroBreakdown.carbsPercent)}%
                        </Badge>
                        <Badge variant="info">
                          F {Math.round(plan.macroBreakdown.fatPercent)}%
                        </Badge>
                      </div>
                    )}
                    <span className="text-xs text-primary font-medium ml-2">
                      View
                    </span>
                  </div>
                </div>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default function PlansPage() {
  return (
    <Suspense
      fallback={
        <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6 space-y-4">
          <CardSkeleton />
          <CardSkeleton />
          <CardSkeleton />
        </div>
      }
    >
      <PlansContent />
    </Suspense>
  );
}
