"use client";

import { useEffect, useMemo, useState } from "react";
import { api, DietPlan, SavedMeal, UserProfile } from "@/lib/api";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { CardSkeleton } from "@/components/ui/Skeleton";
import { Button } from "@/components/ui/Button";
import toast from "react-hot-toast";
import { Bookmark, Trash2, ArrowRightLeft, User } from "lucide-react";

type InsertSelection = {
  planId: string;
  dayIndex: number | null;
  mealIndex: number;
};

function deriveMealCount(plan: DietPlan, dayIndex: number | null): number {
  if (plan.days && plan.days.length > 0) {
    if (dayIndex == null || dayIndex < 0 || dayIndex >= plan.days.length) return 0;
    return plan.days[dayIndex]?.meals?.length ?? 0;
  }
  return plan.meals?.length ?? 0;
}

export default function SavedMealsPage() {
  const [loading, setLoading] = useState(true);
  const [profiles, setProfiles] = useState<UserProfile[]>([]);
  const [plans, setPlans] = useState<DietPlan[]>([]);
  const [savedMeals, setSavedMeals] = useState<SavedMeal[]>([]);
  const [selectedProfile, setSelectedProfile] = useState<string>("all");
  const [insertingId, setInsertingId] = useState<string | null>(null);
  const [insertByMealId, setInsertByMealId] = useState<Record<string, InsertSelection>>({});

  useEffect(() => {
    Promise.all([
      api.profiles.list().catch(() => [] as UserProfile[]),
      api.dietPlans.listAll().catch(() => [] as DietPlan[]),
      api.savedMeals.list().catch(() => [] as SavedMeal[]),
    ])
      .then(([p, d, s]) => {
        setProfiles(p);
        setPlans(d);
        setSavedMeals(s);
      })
      .catch(() => toast.error("Failed to load saved meals"))
      .finally(() => setLoading(false));
  }, []);

  const profileMap = useMemo(() => new Map(profiles.map((p) => [p.id, p])), [profiles]);
  const filteredSavedMeals = useMemo(
    () =>
      selectedProfile === "all"
        ? savedMeals
        : savedMeals.filter((m) => m.profileId === selectedProfile),
    [savedMeals, selectedProfile]
  );

  const plansByProfile = useMemo(() => {
    const map = new Map<string, DietPlan[]>();
    for (const plan of plans) {
      if (!plan.profileId) continue;
      const current = map.get(plan.profileId) || [];
      current.push(plan);
      map.set(plan.profileId, current);
    }
    return map;
  }, [plans]);

  function getSelection(savedMeal: SavedMeal): InsertSelection | null {
    if (!savedMeal.id || !savedMeal.profileId) return null;
    const existing = insertByMealId[savedMeal.id];
    if (existing) return existing;

    const candidates = plansByProfile.get(savedMeal.profileId) || [];
    if (candidates.length === 0) return null;
    const defaultPlan = candidates[0];
    const defaultDay = defaultPlan.days && defaultPlan.days.length > 0 ? 0 : null;
    return { planId: defaultPlan.id || "", dayIndex: defaultDay, mealIndex: 0 };
  }

  function updateSelection(savedMealId: string, next: Partial<InsertSelection>) {
    setInsertByMealId((prev) => {
      const current = prev[savedMealId] || { planId: "", dayIndex: null, mealIndex: 0 };
      return { ...prev, [savedMealId]: { ...current, ...next } };
    });
  }

  async function handleDelete(savedMealId: string) {
    try {
      await api.savedMeals.delete(savedMealId);
      setSavedMeals((prev) => prev.filter((m) => m.id !== savedMealId));
      toast.success("Saved meal deleted");
    } catch {
      toast.error("Failed to delete saved meal");
    }
  }

  async function handleInsert(savedMeal: SavedMeal) {
    if (!savedMeal.id || !savedMeal.profileId) return;
    const selection = getSelection(savedMeal);
    if (!selection || !selection.planId) {
      toast.error("Select a target plan first");
      return;
    }
    setInsertingId(savedMeal.id);
    try {
      await api.savedMeals.insert(
        savedMeal.id,
        selection.planId,
        selection.mealIndex,
        selection.dayIndex === null ? undefined : selection.dayIndex
      );
      toast.success("Saved meal inserted into plan");
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "Failed to insert saved meal");
    } finally {
      setInsertingId(null);
    }
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6 space-y-4">
        <CardSkeleton />
        <CardSkeleton />
        <CardSkeleton />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6 space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Saved Meals</h1>
        <p className="text-sm text-muted-foreground mt-1">
          Keep favorite meals and insert them back into your plans.
        </p>
      </div>

      {profiles.length > 1 && (
        <div className="flex flex-wrap gap-2">
          <button
            onClick={() => setSelectedProfile("all")}
            className={`rounded-full px-3 py-1.5 text-sm font-medium transition-colors ${
              selectedProfile === "all"
                ? "bg-primary text-primary-foreground"
                : "bg-secondary text-secondary-foreground hover:bg-secondary/80"
            }`}
          >
            All Profiles
          </button>
          {profiles.map((profile) => (
            <button
              key={profile.id}
              onClick={() => setSelectedProfile(profile.id || "all")}
              className={`rounded-full px-3 py-1.5 text-sm font-medium transition-colors ${
                selectedProfile === profile.id
                  ? "bg-primary text-primary-foreground"
                  : "bg-secondary text-secondary-foreground hover:bg-secondary/80"
              }`}
            >
              {profile.name}
            </button>
          ))}
        </div>
      )}

      {filteredSavedMeals.length === 0 ? (
        <EmptyState
          icon={Bookmark}
          title="No saved meals yet"
          description="Save meals from a plan, then insert them into a future day slot."
        />
      ) : (
        <div className="space-y-3">
          {filteredSavedMeals.map((savedMeal) => {
            const selection = getSelection(savedMeal);
            const profilePlans = savedMeal.profileId ? plansByProfile.get(savedMeal.profileId) || [] : [];
            const selectedPlan = profilePlans.find((p) => p.id === selection?.planId) || profilePlans[0];
            const dayCount = selectedPlan?.days?.length || 0;
            const mealSlots = selectedPlan ? deriveMealCount(selectedPlan, selection?.dayIndex ?? null) : 0;

            return (
              <Card key={savedMeal.id}>
                <div className="space-y-4">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="font-semibold truncate">{savedMeal.meal?.name || "Saved meal"}</p>
                      <p className="text-xs text-muted-foreground mt-1">
                        {savedMeal.meal?.calories ?? 0} kcal · P {Math.round(savedMeal.meal?.proteinGrams ?? 0)}g · C{" "}
                        {Math.round(savedMeal.meal?.carbsGrams ?? 0)}g · F {Math.round(savedMeal.meal?.fatGrams ?? 0)}g
                      </p>
                      <p className="text-xs text-muted-foreground mt-1 flex items-center gap-1">
                        <User className="h-3 w-3" />
                        {profileMap.get(savedMeal.profileId || "")?.name || "Unknown profile"}
                      </p>
                    </div>
                    {savedMeal.id && (
                      <button
                        type="button"
                        onClick={() => handleDelete(savedMeal.id!)}
                        className="inline-flex items-center justify-center rounded-lg border border-border px-2.5 py-1.5 text-xs text-muted-foreground hover:bg-destructive/10 hover:text-destructive hover:border-destructive/30"
                        title="Delete saved meal"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    )}
                  </div>

                  <div className="flex flex-wrap gap-2">
                    {(savedMeal.meal?.foods || []).map((food, idx) => (
                      <span
                        key={`${savedMeal.id}-food-${idx}`}
                        className="inline-flex items-center rounded-lg bg-secondary px-2.5 py-1 text-xs text-secondary-foreground"
                      >
                        {food.name}
                      </span>
                    ))}
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-4 gap-2">
                    <select
                      className="rounded-lg border border-border bg-background px-3 py-2 text-sm"
                      value={selection?.planId || ""}
                      onChange={(e) =>
                        updateSelection(savedMeal.id || "", {
                          planId: e.target.value,
                          dayIndex: 0,
                          mealIndex: 0,
                        })
                      }
                    >
                      {profilePlans.length === 0 ? (
                        <option value="">No plans for this profile</option>
                      ) : (
                        profilePlans.map((plan) => (
                          <option key={plan.id} value={plan.id}>
                            {plan.createdAt
                              ? new Date(plan.createdAt).toLocaleDateString()
                              : "Saved plan"}{" "}
                            · {plan.totalDays && plan.totalDays > 0 ? `${plan.totalDays} days` : `${plan.meals?.length || 0} meals`}
                          </option>
                        ))
                      )}
                    </select>

                    <select
                      className="rounded-lg border border-border bg-background px-3 py-2 text-sm"
                      value={String(selection?.dayIndex ?? 0)}
                      onChange={(e) =>
                        updateSelection(savedMeal.id || "", {
                          dayIndex: Number(e.target.value),
                          mealIndex: 0,
                        })
                      }
                      disabled={!selectedPlan || dayCount === 0}
                    >
                      {dayCount === 0 ? (
                        <option value="0">Single-day plan</option>
                      ) : (
                        Array.from({ length: dayCount }).map((_, idx) => (
                          <option key={`${savedMeal.id}-day-${idx}`} value={idx}>
                            Day {idx + 1}
                          </option>
                        ))
                      )}
                    </select>

                    <select
                      className="rounded-lg border border-border bg-background px-3 py-2 text-sm"
                      value={String(selection?.mealIndex ?? 0)}
                      onChange={(e) =>
                        updateSelection(savedMeal.id || "", {
                          mealIndex: Number(e.target.value),
                        })
                      }
                      disabled={!selectedPlan || mealSlots === 0}
                    >
                      {mealSlots === 0 ? (
                        <option value="0">No meal slots</option>
                      ) : (
                        Array.from({ length: mealSlots }).map((_, idx) => (
                          <option key={`${savedMeal.id}-slot-${idx}`} value={idx}>
                            Slot {idx + 1}
                          </option>
                        ))
                      )}
                    </select>

                    <Button
                      onClick={() => void handleInsert(savedMeal)}
                      loading={insertingId === savedMeal.id}
                      disabled={!selection?.planId || profilePlans.length === 0}
                      className="justify-center"
                    >
                      <ArrowRightLeft className="h-4 w-4" />
                      Insert
                    </Button>
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
