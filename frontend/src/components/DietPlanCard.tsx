"use client";

import { DietPlan } from "@/lib/api";
import { Card, CardHeader, CardTitle } from "./ui/Card";
import { Badge } from "./ui/Badge";
import { SafetyAlertBanner } from "./SafetyAlertBanner";
import { NutrientAuditPanel } from "./NutrientAuditPanel";
import { EvidenceTagList } from "./EvidenceBadge";
import { Utensils, Flame, Beef, Wheat, Droplets, Scale } from "lucide-react";

interface DietPlanCardProps {
  plan: DietPlan;
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

export function DietPlanCard({ plan }: DietPlanCardProps) {
  return (
    <div className="space-y-6 animate-fade-in">
      {/* Safety alerts */}
      <SafetyAlertBanner alerts={plan.safetyAlerts} safetyCleared={plan.safetyCleared} />

      {/* Version indicator */}
      {plan.version && plan.version > 1 && (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Scale className="h-4 w-4" />
          <span>Adapted plan (v{plan.version})</span>
          {plan.parentPlanId && (
            <span className="text-xs">from plan {plan.parentPlanId.substring(0, 8)}...</span>
          )}
        </div>
      )}

      {/* Summary row */}
      {plan.dailyCalories > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Card className="text-center">
            <Flame className="h-8 w-8 mx-auto text-accent mb-2" />
            <p className="text-2xl font-bold">{plan.dailyCalories}</p>
            <p className="text-xs text-muted-foreground">Daily Calories</p>
          </Card>
          <Card className="text-center">
            <Utensils className="h-8 w-8 mx-auto text-primary mb-2" />
            <p className="text-2xl font-bold">{plan.meals?.length || 0}</p>
            <p className="text-xs text-muted-foreground">Meals Per Day</p>
          </Card>
          <Card className="flex items-center justify-center gap-6">
            {plan.macroBreakdown && (
              <>
                <MacroRing
                  label="Protein"
                  percent={plan.macroBreakdown.proteinPercent}
                  color="var(--primary)"
                />
                <MacroRing
                  label="Carbs"
                  percent={plan.macroBreakdown.carbsPercent}
                  color="var(--accent)"
                />
                <MacroRing
                  label="Fat"
                  percent={plan.macroBreakdown.fatPercent}
                  color="#8b5cf6"
                />
              </>
            )}
          </Card>
        </div>
      )}

      {/* Evidence tags */}
      {plan.evidenceTags && plan.evidenceTags.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-sm">Evidence Basis</CardTitle>
          </CardHeader>
          <EvidenceTagList tags={plan.evidenceTags} />
        </Card>
      )}

      {/* Nutrient audit */}
      <NutrientAuditPanel audit={plan.nutrientAudit} />

      {/* Meals */}
      {plan.meals && plan.meals.length > 0 && (
        <div className="space-y-4">
          <h2 className="text-xl font-semibold">Meal Breakdown</h2>
          {plan.meals.map((meal, index) => (
            <Card key={index} hover>
              <CardHeader className="flex flex-row items-center justify-between mb-0 pb-3 border-b border-border">
                <CardTitle className="flex items-center gap-2">
                  <Utensils className="h-4 w-4 text-primary" />
                  {meal.name}
                </CardTitle>
                <Badge variant="info">{meal.calories} kcal</Badge>
              </CardHeader>

              <div className="pt-4">
                <div className="flex flex-wrap gap-2 mb-3">
                  {meal.foods?.map((food, fi) => (
                    <span
                      key={fi}
                      className="rounded-lg bg-secondary px-3 py-1 text-sm text-secondary-foreground"
                      title={food.fdcId ? `FDC #${food.fdcId}` : undefined}
                    >
                      {typeof food === "string" ? food : `${food.name}${food.quantityGrams ? ` (${food.quantityGrams}g)` : ""}`}
                    </span>
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
          ))}
        </div>
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
