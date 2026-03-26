"use client";

import { SafetyAlertData } from "@/lib/api";
import { AlertTriangle, ShieldX, ShieldCheck } from "lucide-react";

export function SafetyAlertBanner({
  alerts,
  safetyCleared,
}: {
  alerts?: SafetyAlertData[];
  safetyCleared?: boolean;
}) {
  if (!alerts || alerts.length === 0) {
    if (safetyCleared) {
      return (
        <div className="flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 dark:border-emerald-800 dark:bg-emerald-900/20 px-4 py-3 text-sm text-emerald-800 dark:text-emerald-300">
          <ShieldCheck className="h-5 w-5 shrink-0" />
          <span>All safety checks passed</span>
        </div>
      );
    }
    return null;
  }

  const blocks = alerts.filter((a) => a.severity === "BLOCK");
  const warnings = alerts.filter((a) => a.severity === "WARNING");

  return (
    <div className="space-y-3">
      {blocks.map((alert, i) => (
        <div
          key={`block-${i}`}
          className="flex gap-3 rounded-xl border border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-900/20 px-4 py-3"
        >
          <ShieldX className="h-5 w-5 shrink-0 text-red-600 dark:text-red-400 mt-0.5" />
          <div className="text-sm">
            <p className="font-medium text-red-800 dark:text-red-300">{alert.message}</p>
            <p className="text-red-600/80 dark:text-red-400/80 mt-1">{alert.recommendation}</p>
            <span className="inline-block mt-1.5 rounded-full bg-red-200 dark:bg-red-800 px-2 py-0.5 text-xs font-medium text-red-800 dark:text-red-200">
              {alert.checkType}
            </span>
          </div>
        </div>
      ))}
      {warnings.map((alert, i) => (
        <div
          key={`warn-${i}`}
          className="flex gap-3 rounded-xl border border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-900/20 px-4 py-3"
        >
          <AlertTriangle className="h-5 w-5 shrink-0 text-amber-600 dark:text-amber-400 mt-0.5" />
          <div className="text-sm">
            <p className="font-medium text-amber-800 dark:text-amber-300">{alert.message}</p>
            <p className="text-amber-600/80 dark:text-amber-400/80 mt-1">{alert.recommendation}</p>
            <span className="inline-block mt-1.5 rounded-full bg-amber-200 dark:bg-amber-800 px-2 py-0.5 text-xs font-medium text-amber-800 dark:text-amber-200">
              {alert.checkType}
            </span>
          </div>
        </div>
      ))}
    </div>
  );
}
