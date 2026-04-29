"use client";

import { NutrientAudit } from "@/lib/api";
import { useState } from "react";
import { ChevronDown, ChevronUp, Activity } from "lucide-react";

const STATUS_STYLES: Record<string, string> = {
  ADEQUATE: "text-emerald-600 dark:text-emerald-400",
  LOW: "text-amber-600 dark:text-amber-400",
  DEFICIENT: "text-red-600 dark:text-red-400",
  EXCESSIVE: "text-red-600 dark:text-red-400",
  UNKNOWN: "text-slate-600 dark:text-slate-300",
};

const STATUS_BG: Record<string, string> = {
  ADEQUATE: "bg-emerald-100 dark:bg-emerald-900/30",
  LOW: "bg-amber-100 dark:bg-amber-900/30",
  DEFICIENT: "bg-red-100 dark:bg-red-900/30",
  EXCESSIVE: "bg-red-100 dark:bg-red-900/30",
  UNKNOWN: "bg-slate-100 dark:bg-slate-700/40",
};

function formatNutrientName(key: string): string {
  return key
    .replace(/_/g, " ")
    .replace(/\b\w/g, (l) => l.toUpperCase());
}

export function NutrientAuditPanel({ audit }: { audit?: NutrientAudit }) {
  const [expanded, setExpanded] = useState(false);

  if (!audit || !audit.nutrients) return null;

  const entries = Object.entries(audit.nutrients);
  const deficient = entries.filter(([, v]) => v.status === "DEFICIENT" || v.status === "LOW").length;
  const excessive = entries.filter(([, v]) => v.status === "EXCESSIVE").length;
  const unknown = entries.filter(([, v]) => v.status === "UNKNOWN").length;

  return (
    <div className="rounded-xl border border-border bg-card overflow-hidden">
      <button
        onClick={() => setExpanded(!expanded)}
        className="flex items-center justify-between w-full px-4 py-3 text-left hover:bg-secondary/50 transition-colors"
      >
        <div className="flex items-center gap-3">
          <Activity className="h-5 w-5 text-primary" />
          <div>
            <span className="text-sm font-medium">Nutrient Adequacy</span>
            <span className="ml-2 text-sm text-muted-foreground">
              Score: {audit.adequacyScore}%
            </span>
          </div>
          {deficient > 0 && (
            <span className="rounded-full bg-amber-100 dark:bg-amber-900/30 px-2 py-0.5 text-xs font-medium text-amber-700 dark:text-amber-300">
              {deficient} low
            </span>
          )}
          {excessive > 0 && (
            <span className="rounded-full bg-red-100 dark:bg-red-900/30 px-2 py-0.5 text-xs font-medium text-red-700 dark:text-red-300">
              {excessive} excess
            </span>
          )}
          {unknown > 0 && (
            <span className="rounded-full bg-slate-100 dark:bg-slate-700/40 px-2 py-0.5 text-xs font-medium text-slate-700 dark:text-slate-300">
              {unknown} unknown
            </span>
          )}
        </div>
        {expanded ? (
          <ChevronUp className="h-4 w-4 text-muted-foreground" />
        ) : (
          <ChevronDown className="h-4 w-4 text-muted-foreground" />
        )}
      </button>

      {expanded && (
        <div className="border-t border-border px-4 py-3">
          <div className="mb-3 text-xs text-muted-foreground space-y-1">
            {typeof audit.dataCoveragePercent === "number" && (
              <p>
                Data coverage: {audit.dataCoveragePercent.toFixed(0)}% ({audit.knownNutrientCount ?? 0} known, {audit.unknownNutrientCount ?? 0} unknown)
              </p>
            )}
            {audit.proteinGoalAdjusted && (
              <p>Protein is evaluated against a goal-adjusted target range.</p>
            )}
          </div>
          <div className="grid grid-cols-1 gap-1">
            <div className="grid grid-cols-5 text-xs font-medium text-muted-foreground pb-2 border-b border-border">
              <span>Nutrient</span>
              <span className="text-right">Planned</span>
              <span className="text-right">RDA</span>
              <span className="text-right">UL</span>
              <span className="text-right">Status</span>
            </div>
            {entries.map(([key, status]) => (
              <div
                key={key}
                className="grid grid-cols-5 text-xs py-1.5 border-b border-border/50 last:border-0"
              >
                <span className="font-medium text-foreground">
                  {formatNutrientName(key)}
                </span>
                <span className="text-right text-muted-foreground">
                  {status.planned.toFixed(1)} {status.unit}
                </span>
                <span className="text-right text-muted-foreground">
                  {status.rda > 0 ? `${status.rda.toFixed(1)}` : "—"}
                  {status.targetType === "GOAL_RANGE" ? " (goal)" : ""}
                </span>
                <span className="text-right text-muted-foreground">
                  {status.ul > 0 ? `${status.ul.toFixed(1)}` : "—"}
                </span>
                <span className="text-right">
                  <span
                    className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_BG[status.status] || ""} ${STATUS_STYLES[status.status] || ""}`}
                  >
                    {status.status}
                  </span>
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
