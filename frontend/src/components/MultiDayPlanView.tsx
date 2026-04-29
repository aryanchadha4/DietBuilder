"use client";

import { useState, useRef } from "react";
import { DietPlan, DayPlan, Meal, MealFood, GroceryList } from "@/lib/api";
import { Card, CardHeader, CardTitle } from "./ui/Card";
import { Badge } from "./ui/Badge";
import { SafetyAlertBanner } from "./SafetyAlertBanner";
import { NutrientAuditPanel } from "./NutrientAuditPanel";
import { EvidenceTagList } from "./EvidenceBadge";
import { FoodRejectionPopover } from "./FoodRejectionPopover";
import { ConfidenceIndicator } from "./ConfidenceIndicator";
import { ConflictNoteCard } from "./ConflictNoteCard";
import { CitationPanel } from "./CitationPanel";
import {
  Utensils,
  Flame,
  Beef,
  Wheat,
  Droplets,
  Scale,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  LayoutGrid,
  Trash2,
  RefreshCw,
  BookmarkPlus,
  ShoppingBasket,
} from "lucide-react";

interface MultiDayPlanViewProps {
  plan: DietPlan;
  onRejectFood?: (
    foodName: string,
    type: "PERMANENT" | "TEMPORARY",
    reason?: string,
    planId?: string
  ) => void;
  /** Persisted plans only: removes the meal from Mongo without changing food preferences. */
  onRemoveMeal?: (ctx: {
    dayIndex: number | null;
    mealIndex: number;
  }) => Promise<void>;
  onReplaceRemovedMeals?: () => void;
  replaceRemovedLoading?: boolean;
  onSaveMeal?: (ctx: { dayIndex: number | null; mealIndex: number }) => Promise<void>;
  onGenerateGroceryList?: (planId: string) => Promise<GroceryList>;
}

function MacroRing({
  label,
  percent,
  color,
}: {
  label: string;
  percent: number;
  color: string;
}) {
  const radius = 28;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference - (percent / 100) * circumference;

  return (
    <div className="flex flex-col items-center gap-1">
      <div className="relative h-16 w-16">
        <svg className="h-16 w-16 -rotate-90" viewBox="0 0 64 64">
          <circle
            cx="32"
            cy="32"
            r={radius}
            fill="none"
            stroke="var(--border)"
            strokeWidth="4"
          />
          <circle
            cx="32"
            cy="32"
            r={radius}
            fill="none"
            stroke={color}
            strokeWidth="4"
            strokeDasharray={circumference}
            strokeDashoffset={offset}
            strokeLinecap="round"
            className="transition-all duration-700"
          />
        </svg>
        <span className="absolute inset-0 flex items-center justify-center text-xs font-bold">
          {Math.round(percent)}%
        </span>
      </div>
      <span className="text-xs text-muted-foreground">{label}</span>
    </div>
  );
}

function FoodChip({
  food,
  onReject,
}: {
  food: MealFood | string;
  onReject?: (name: string, type: "PERMANENT" | "TEMPORARY", reason?: string) => void;
}) {
  const [showPopover, setShowPopover] = useState(false);
  const name = typeof food === "string" ? food : food.name;
  const grams = typeof food === "string" ? null : food.quantityGrams;
  const fdcId = typeof food === "string" ? null : food.fdcId;

  return (
    <span
      className="group relative inline-flex items-center rounded-lg bg-secondary px-3 py-1 text-sm text-secondary-foreground"
      title={fdcId ? `FDC #${fdcId}` : undefined}
    >
      {name}
      {grams ? ` (${grams}g)` : ""}
      {onReject && (
        <button
          onClick={(e) => {
            e.stopPropagation();
            setShowPopover(true);
          }}
          className="ml-1.5 hidden group-hover:inline-flex items-center justify-center h-4 w-4 rounded-full bg-red-200 text-red-700 hover:bg-red-300 text-xs leading-none"
          title={`Reject ${name}`}
        >
          &times;
        </button>
      )}
      {showPopover && onReject && (
        <FoodRejectionPopover
          foodName={name}
          onReject={(foodName, type, reason) => {
            onReject(foodName, type, reason);
            setShowPopover(false);
          }}
          onClose={() => setShowPopover(false)}
        />
      )}
    </span>
  );
}

function MealCard({
  meal,
  onRejectFood,
  onRemoveMeal,
  onSaveMeal,
  removeDisabled,
  saveDisabled,
}: {
  meal: Meal;
  onRejectFood?: (foodName: string, type: "PERMANENT" | "TEMPORARY", reason?: string) => void;
  onRemoveMeal?: () => void;
  onSaveMeal?: () => void;
  removeDisabled?: boolean;
  saveDisabled?: boolean;
}) {
  return (
    <Card hover>
      <CardHeader className="flex flex-row items-center justify-between mb-0 pb-3 border-b border-border gap-2">
        <CardTitle className="flex items-center gap-2 min-w-0 flex-1">
          <Utensils className="h-4 w-4 text-primary shrink-0" />
          <span className="truncate">{meal.name}</span>
        </CardTitle>
        <div className="flex items-center gap-2 shrink-0">
          {onSaveMeal && (
            <button
              type="button"
              onClick={onSaveMeal}
              disabled={saveDisabled}
              className="inline-flex items-center justify-center rounded-lg border border-border px-2 py-1 text-xs text-muted-foreground hover:bg-primary/10 hover:text-primary hover:border-primary/30 disabled:opacity-40"
              title="Save this meal to your Saved Meals section"
            >
              <BookmarkPlus className="h-3.5 w-3.5" />
            </button>
          )}
          {onRemoveMeal && (
            <button
              type="button"
              onClick={() => {
                if (
                  typeof window !== "undefined" &&
                  window.confirm(
                    `Remove "${meal.name}" from this plan? This does not add foods to your exclusion list.`
                  )
                ) {
                  onRemoveMeal();
                }
              }}
              disabled={removeDisabled}
              className="inline-flex items-center justify-center rounded-lg border border-border px-2 py-1 text-xs text-muted-foreground hover:bg-destructive/10 hover:text-destructive hover:border-destructive/30 disabled:opacity-40"
              title="Remove this meal from the saved plan"
            >
              <Trash2 className="h-3.5 w-3.5" />
            </button>
          )}
          <Badge variant="info">{meal.calories} kcal</Badge>
        </div>
      </CardHeader>

      <div className="pt-4">
        <div className="flex flex-wrap gap-2 mb-3">
          {meal.foods?.map((food, fi) => (
            <FoodChip key={fi} food={food} onReject={onRejectFood} />
          ))}
        </div>

        <div className="flex items-center gap-4 text-xs text-muted-foreground">
          <span className="flex items-center gap-1">
            <Beef className="h-3.5 w-3.5 text-primary" />
            {meal.proteinGrams}g protein
          </span>
          <span className="flex items-center gap-1">
            <Wheat className="h-3.5 w-3.5 text-accent" />
            {meal.carbsGrams}g carbs
          </span>
          <span className="flex items-center gap-1">
            <Droplets className="h-3.5 w-3.5 text-purple-500" />
            {meal.fatGrams}g fat
          </span>
        </div>
      </div>
    </Card>
  );
}

function DaySummaryCard({
  day,
  selected,
  onClick,
}: {
  day: DayPlan;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={`flex-shrink-0 rounded-xl border px-4 py-3 text-left transition-all min-w-[140px]
        ${
          selected
            ? "border-primary bg-primary/10 shadow-sm"
            : "border-border bg-card hover:border-primary/40"
        }`}
    >
      <p className="text-xs font-semibold text-primary">Day {day.dayNumber}</p>
      <p className="text-[11px] text-muted-foreground truncate mt-0.5">
        {day.label?.replace(/^Day \d+\s*[-–]\s*/, "") || ""}
      </p>
      <p className="text-sm font-bold mt-1">{day.dailyCalories} kcal</p>
      <p className="text-[10px] text-muted-foreground">
        {day.meals?.length || 0} meals
      </p>
    </button>
  );
}

function OverviewGrid({
  days,
  onSelectDay,
}: {
  days: DayPlan[];
  onSelectDay: (idx: number) => void;
}) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
      {days.map((day, idx) => (
        <Card
          key={day.dayNumber}
          hover
          className="cursor-pointer"
          onClick={() => onSelectDay(idx)}
        >
          <div className="flex items-center justify-between mb-2">
            <span className="text-sm font-semibold text-primary">
              Day {day.dayNumber}
            </span>
            <Badge variant="info">{day.dailyCalories} kcal</Badge>
          </div>
          <p className="text-xs text-muted-foreground mb-2">
            {day.label?.replace(/^Day \d+\s*[-–]\s*/, "") || ""}
          </p>
          <div className="space-y-1">
            {day.meals?.map((meal, mi) => (
              <div
                key={mi}
                className="flex items-center justify-between text-xs"
              >
                <span className="text-muted-foreground">{meal.name}</span>
                <span className="font-medium">{meal.calories} kcal</span>
              </div>
            ))}
          </div>
          {day.macroBreakdown && (
            <div className="flex gap-3 mt-2 text-[10px] text-muted-foreground">
              <span>P {Math.round(day.macroBreakdown.proteinPercent)}%</span>
              <span>C {Math.round(day.macroBreakdown.carbsPercent)}%</span>
              <span>F {Math.round(day.macroBreakdown.fatPercent)}%</span>
            </div>
          )}
        </Card>
      ))}
    </div>
  );
}

export function MultiDayPlanView({
  plan,
  onRejectFood,
  onRemoveMeal,
  onReplaceRemovedMeals,
  replaceRemovedLoading,
  onSaveMeal,
  onGenerateGroceryList,
}: MultiDayPlanViewProps) {
  const days = plan.days || [];
  const isMultiDay = days.length > 0;

  const [selectedDayIdx, setSelectedDayIdx] = useState(0);
  const [showOverview, setShowOverview] = useState(false);
  const [removingKey, setRemovingKey] = useState<string | null>(null);
  const [savingKey, setSavingKey] = useState<string | null>(null);
  const [groceryList, setGroceryList] = useState<GroceryList | null>(null);
  const [groceryLoading, setGroceryLoading] = useState(false);
  const [groceryError, setGroceryError] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  const canRemoveMeal = Boolean(plan.id && onRemoveMeal);
  const canSaveMeal = Boolean(plan.id && onSaveMeal);

  async function runRemoveMeal(
    key: string,
    dayIndex: number | null,
    mealIndex: number
  ) {
    if (!onRemoveMeal) return;
    setRemovingKey(key);
    try {
      await onRemoveMeal({ dayIndex, mealIndex });
    } finally {
      setRemovingKey(null);
    }
  }

  async function runSaveMeal(key: string, dayIndex: number | null, mealIndex: number) {
    if (!onSaveMeal) return;
    setSavingKey(key);
    try {
      await onSaveMeal({ dayIndex, mealIndex });
    } finally {
      setSavingKey(null);
    }
  }

  async function runGenerateGroceryList() {
    if (!plan.id || !onGenerateGroceryList) return;
    setGroceryLoading(true);
    setGroceryError(null);
    try {
      const response = await onGenerateGroceryList(plan.id);
      setGroceryList(response);
    } catch {
      setGroceryError("Failed to generate grocery list. Please try again.");
    } finally {
      setGroceryLoading(false);
    }
  }

  if (!isMultiDay) {
    // Legacy single-day fallback
    const meals = plan.meals || [];
    return (
      <div className="space-y-6 animate-fade-in">
        <SafetyAlertBanner
          alerts={plan.safetyAlerts}
          safetyCleared={plan.safetyCleared}
        />

        {plan.version && plan.version > 1 && (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Scale className="h-4 w-4" />
            <span>Adapted plan (v{plan.version})</span>
          </div>
        )}

        {plan.dailyCalories > 0 && (
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <Card className="text-center">
              <Flame className="h-8 w-8 mx-auto text-accent mb-2" />
              <p className="text-2xl font-bold">{plan.dailyCalories}</p>
              <p className="text-xs text-muted-foreground">Daily Calories</p>
            </Card>
            <Card className="text-center">
              <Utensils className="h-8 w-8 mx-auto text-primary mb-2" />
              <p className="text-2xl font-bold">{meals.length}</p>
              <p className="text-xs text-muted-foreground">Meals Per Day</p>
            </Card>
            <Card className="flex items-center justify-center gap-6">
              {plan.macroBreakdown && (
                <>
                  <MacroRing label="Protein" percent={plan.macroBreakdown.proteinPercent} color="var(--primary)" />
                  <MacroRing label="Carbs" percent={plan.macroBreakdown.carbsPercent} color="var(--accent)" />
                  <MacroRing label="Fat" percent={plan.macroBreakdown.fatPercent} color="#8b5cf6" />
                </>
              )}
            </Card>
          </div>
        )}

        <NutrientAuditPanel audit={plan.nutrientAudit} />
        {plan.id && onGenerateGroceryList && (
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle className="text-base">Grocery List</CardTitle>
              <button
                type="button"
                onClick={runGenerateGroceryList}
                disabled={groceryLoading}
                className="inline-flex items-center gap-2 rounded-xl bg-primary px-3 py-2 text-xs font-medium text-primary-foreground hover:bg-primary-dark disabled:opacity-60 disabled:cursor-not-allowed"
              >
                <ShoppingBasket className="h-3.5 w-3.5" />
                {groceryLoading ? "Generating..." : "Generate Grocery List"}
              </button>
            </CardHeader>
            {groceryError && (
              <p className="text-sm text-destructive">{groceryError}</p>
            )}
            {groceryList && (
              <div className="space-y-2">
                <p className="text-xs text-muted-foreground">
                  {groceryList.totalItems} items
                </p>
                {groceryList.foods.length > 0 ? (
                  <ul className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-1 text-sm">
                    {groceryList.foods.map((food) => (
                      <li key={food} className="list-disc ml-4">
                        {food}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-sm text-muted-foreground">
                    No foods available in this plan yet.
                  </p>
                )}
              </div>
            )}
          </Card>
        )}

        {meals.length > 0 && (
          <div className="space-y-4">
            <h2 className="text-xl font-semibold">Meal Breakdown</h2>
            {meals.map((meal, i) => (
              <MealCard
                key={i}
                meal={meal}
                onRejectFood={onRejectFood ? (name, type, reason) => onRejectFood(name, type, reason, plan.id) : undefined}
                onRemoveMeal={
                  canRemoveMeal
                    ? () => void runRemoveMeal(`legacy-${i}`, null, i)
                    : undefined
                }
                onSaveMeal={
                  canSaveMeal
                    ? () => void runSaveMeal(`legacy-${i}`, null, i)
                    : undefined
                }
                removeDisabled={removingKey !== null}
                saveDisabled={savingKey !== null}
              />
            ))}
          </div>
        )}

        {plan.notes && (
          <Card>
            <CardHeader><CardTitle>Expert Notes</CardTitle></CardHeader>
            <p className="text-sm text-muted-foreground whitespace-pre-wrap leading-relaxed">{plan.notes}</p>
          </Card>
        )}
      </div>
    );
  }

  const selectedDay = days[selectedDayIdx];
  const inferredSources =
    plan.evidenceTags
      ?.filter((tag) => !!tag.sourceId)
      .map((tag) => ({
        id: tag.sourceId,
        title: tag.source || tag.claim,
        summary: tag.simpleSummary || tag.explanation,
        url: tag.url,
        doi: tag.doi,
        sourceType: tag.level,
        credibilityScore: tag.relevanceScore || 0.7,
      })) || [];

  const scroll = (dir: "left" | "right") => {
    if (scrollRef.current) {
      scrollRef.current.scrollBy({
        left: dir === "left" ? -200 : 200,
        behavior: "smooth",
      });
    }
  };

  const avgCalories = Math.round(
    days.reduce((s, d) => s + d.dailyCalories, 0) / days.length
  );

  return (
    <div className="space-y-6 animate-fade-in">
      <SafetyAlertBanner
        alerts={plan.safetyAlerts}
        safetyCleared={plan.safetyCleared}
      />

      {plan.version && plan.version > 1 && (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Scale className="h-4 w-4" />
          <span>Adapted plan (v{plan.version})</span>
          {plan.parentPlanId && (
            <span className="text-xs">
              from plan {plan.parentPlanId.substring(0, 8)}...
            </span>
          )}
        </div>
      )}

      {/* Plan summary */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        <Card className="text-center">
          <CalendarDays className="h-8 w-8 mx-auto text-primary mb-2" />
          <p className="text-2xl font-bold">{days.length}</p>
          <p className="text-xs text-muted-foreground">Days</p>
        </Card>
        <Card className="text-center">
          <Flame className="h-8 w-8 mx-auto text-accent mb-2" />
          <p className="text-2xl font-bold">{avgCalories}</p>
          <p className="text-xs text-muted-foreground">Avg Daily Calories</p>
        </Card>
        <Card className="text-center">
          <Utensils className="h-8 w-8 mx-auto text-primary mb-2" />
          <p className="text-2xl font-bold">
            {Math.round(
              days.reduce((s, d) => s + (d.meals?.length || 0), 0) / days.length
            )}
          </p>
          <p className="text-xs text-muted-foreground">Avg Meals/Day</p>
        </Card>
        <Card className="flex items-center justify-center gap-6">
          {plan.macroBreakdown && (
            <>
              <MacroRing label="Protein" percent={plan.macroBreakdown.proteinPercent} color="var(--primary)" />
              <MacroRing label="Carbs" percent={plan.macroBreakdown.carbsPercent} color="var(--accent)" />
              <MacroRing label="Fat" percent={plan.macroBreakdown.fatPercent} color="#8b5cf6" />
            </>
          )}
        </Card>
      </div>
      <ConfidenceIndicator
        score={plan.evidenceConfidenceScore || 0}
        statement={plan.confidenceStatement}
      />
      {(plan.evidenceConfidenceScore || 0) < 0.4 && (
        <Card>
          <p className="text-sm text-amber-700 dark:text-amber-400">
            This plan has limited supporting evidence. Consider consulting a healthcare provider.
          </p>
        </Card>
      )}

      {/* Evidence & Audit */}
      {plan.evidenceTags && plan.evidenceTags.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-sm">Evidence Basis</CardTitle>
          </CardHeader>
          <EvidenceTagList tags={plan.evidenceTags} />
        </Card>
      )}
      <CitationPanel sources={inferredSources} />
      {plan.conflictNotes && plan.conflictNotes.length > 0 && (
        <div className="space-y-3">
          {plan.conflictNotes.map((note, idx) => (
            <ConflictNoteCard key={`${note.topic}-${idx}`} note={note} />
          ))}
        </div>
      )}
      <NutrientAuditPanel audit={plan.nutrientAudit} />
      {plan.id && onGenerateGroceryList && (
        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle className="text-base">Grocery List</CardTitle>
            <button
              type="button"
              onClick={runGenerateGroceryList}
              disabled={groceryLoading}
              className="inline-flex items-center gap-2 rounded-xl bg-primary px-3 py-2 text-xs font-medium text-primary-foreground hover:bg-primary-dark disabled:opacity-60 disabled:cursor-not-allowed"
            >
              <ShoppingBasket className="h-3.5 w-3.5" />
              {groceryLoading ? "Generating..." : "Generate Grocery List"}
            </button>
          </CardHeader>
          {groceryError && (
            <p className="text-sm text-destructive">{groceryError}</p>
          )}
          {groceryList && (
            <div className="space-y-2">
              <p className="text-xs text-muted-foreground">
                {groceryList.totalItems} items
              </p>
              {groceryList.foods.length > 0 ? (
                <ul className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-1 text-sm">
                  {groceryList.foods.map((food) => (
                    <li key={food} className="list-disc ml-4">
                      {food}
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-sm text-muted-foreground">
                  No foods available in this plan yet.
                </p>
              )}
            </div>
          )}
        </Card>
      )}

      {/* Day selector */}
      <div className="flex items-center gap-2">
        <button
          onClick={() => setShowOverview(!showOverview)}
          className={`flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors
            ${showOverview ? "bg-primary text-primary-foreground" : "bg-secondary text-secondary-foreground hover:bg-secondary/80"}`}
        >
          <LayoutGrid className="h-4 w-4" />
          Overview
        </button>
        <div className="flex-1" />
        {!showOverview && (
          <>
            <button
              onClick={() => scroll("left")}
              className="rounded-lg p-1.5 hover:bg-secondary transition-colors"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
            <button
              onClick={() => scroll("right")}
              className="rounded-lg p-1.5 hover:bg-secondary transition-colors"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
          </>
        )}
      </div>

      {showOverview ? (
        <OverviewGrid
          days={days}
          onSelectDay={(idx) => {
            setSelectedDayIdx(idx);
            setShowOverview(false);
          }}
        />
      ) : (
        <>
          {/* Scrollable day tabs */}
          <div
            ref={scrollRef}
            className="flex gap-2 overflow-x-auto pb-2 scrollbar-thin"
          >
            {days.map((day, idx) => (
              <DaySummaryCard
                key={day.dayNumber}
                day={day}
                selected={idx === selectedDayIdx}
                onClick={() => setSelectedDayIdx(idx)}
              />
            ))}
          </div>

          {/* Selected day detail */}
          {selectedDay && (
            <div className="space-y-4 animate-fade-in" key={selectedDay.dayNumber}>
              <div className="flex items-center justify-between">
                <h2 className="text-xl font-semibold">
                  {selectedDay.label || `Day ${selectedDay.dayNumber}`}
                </h2>
                <div className="flex items-center gap-3">
                  <Badge variant="info">
                    {selectedDay.dailyCalories} kcal
                  </Badge>
                  {selectedDay.macroBreakdown && (
                    <span className="text-xs text-muted-foreground">
                      P{Math.round(selectedDay.macroBreakdown.proteinPercent)}%
                      {" / "}C{Math.round(selectedDay.macroBreakdown.carbsPercent)}%
                      {" / "}F{Math.round(selectedDay.macroBreakdown.fatPercent)}%
                    </span>
                  )}
                </div>
              </div>

              {selectedDay.meals?.map((meal, mi) => (
                <MealCard
                  key={mi}
                  meal={meal}
                  onRejectFood={
                    onRejectFood
                      ? (name, type, reason) => onRejectFood(name, type, reason, plan.id)
                      : undefined
                  }
                  onRemoveMeal={
                    canRemoveMeal
                      ? () =>
                          void runRemoveMeal(
                            `d${selectedDayIdx}-m${mi}`,
                            selectedDayIdx,
                            mi
                          )
                      : undefined
                  }
                  onSaveMeal={
                    canSaveMeal
                      ? () =>
                          void runSaveMeal(
                            `d${selectedDayIdx}-m${mi}`,
                            selectedDayIdx,
                            mi
                          )
                      : undefined
                  }
                  removeDisabled={removingKey !== null}
                  saveDisabled={savingKey !== null}
                />
              ))}

              {(plan.removedMealSlots?.length ?? 0) > 0 && onReplaceRemovedMeals && (
                <div className="rounded-xl border border-blue-200 bg-blue-50/50 dark:border-blue-800 dark:bg-blue-900/10 p-4 flex items-center justify-between animate-fade-in">
                  <div className="flex items-center gap-2 min-w-0">
                    <RefreshCw className="h-5 w-5 text-blue-600 shrink-0" />
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-blue-800 dark:text-blue-300">
                        {plan.removedMealSlots?.length} removed meal
                        {(plan.removedMealSlots?.length ?? 0) !== 1 ? "s" : ""} pending replacement
                      </p>
                      <p className="text-xs text-blue-600 dark:text-blue-400">
                        Keep the rest of the plan unchanged and fill only removed slots.
                      </p>
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={onReplaceRemovedMeals}
                    disabled={Boolean(replaceRemovedLoading)}
                    className="inline-flex items-center gap-2 rounded-xl bg-primary px-3 py-2 text-xs font-medium text-primary-foreground hover:bg-primary-dark disabled:opacity-60 disabled:cursor-not-allowed shrink-0"
                  >
                    <RefreshCw className="h-3.5 w-3.5" />
                    Replace Removed Meals
                  </button>
                </div>
              )}

              {/* Day navigation */}
              <div className="flex items-center justify-between pt-2">
                <button
                  onClick={() =>
                    setSelectedDayIdx((p) => Math.max(0, p - 1))
                  }
                  disabled={selectedDayIdx === 0}
                  className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground disabled:opacity-30 transition-colors"
                >
                  <ChevronLeft className="h-4 w-4" />
                  Previous Day
                </button>
                <span className="text-xs text-muted-foreground">
                  Day {selectedDayIdx + 1} of {days.length}
                </span>
                <button
                  onClick={() =>
                    setSelectedDayIdx((p) =>
                      Math.min(days.length - 1, p + 1)
                    )
                  }
                  disabled={selectedDayIdx === days.length - 1}
                  className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground disabled:opacity-30 transition-colors"
                >
                  Next Day
                  <ChevronRight className="h-4 w-4" />
                </button>
              </div>
            </div>
          )}
        </>
      )}

      {/* Notes */}
      {plan.notes && (
        <Card>
          <CardHeader>
            <CardTitle>Expert Notes</CardTitle>
          </CardHeader>
          <p className="text-sm text-muted-foreground whitespace-pre-wrap leading-relaxed">
            {plan.notes}
          </p>
        </Card>
      )}

      {/* Full plan content */}
      {plan.planContent && (
        <Card>
          <CardHeader>
            <CardTitle>Detailed Plan & Rationale</CardTitle>
          </CardHeader>
          <div className="prose prose-sm max-w-none text-muted-foreground">
            <div className="whitespace-pre-wrap text-sm leading-relaxed">
              {plan.planContent}
            </div>
          </div>
        </Card>
      )}
    </div>
  );
}
