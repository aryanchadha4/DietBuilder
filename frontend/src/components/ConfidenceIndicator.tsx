"use client";

import { Badge } from "./ui/Badge";

interface ConfidenceIndicatorProps {
  score?: number;
  statement?: string;
}

export function ConfidenceIndicator({ score = 0, statement }: ConfidenceIndicatorProps) {
  const pct = Math.round(score * 100);
  const band = score > 0.7 ? "high" : score >= 0.4 ? "medium" : "low";
  const colorClass =
    band === "high"
      ? "bg-emerald-500"
      : band === "medium"
      ? "bg-amber-500"
      : "bg-red-500";

  return (
    <div className="rounded-lg border border-border p-3 space-y-2">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium">Evidence Confidence</span>
        <Badge variant="info">{pct}%</Badge>
      </div>
      <div className="h-2 w-full rounded-full bg-secondary">
        <div className={`h-2 rounded-full ${colorClass}`} style={{ width: `${pct}%` }} />
      </div>
      {statement && <p className="text-xs text-muted-foreground">{statement}</p>}
    </div>
  );
}
