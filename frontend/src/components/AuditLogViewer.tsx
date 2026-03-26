"use client";

import { RecommendationLog } from "@/lib/api";
import { useState } from "react";
import { ChevronDown, ChevronUp, FileText } from "lucide-react";

export function AuditLogViewer({ log }: { log?: RecommendationLog | null }) {
  const [expanded, setExpanded] = useState(false);

  if (!log) return null;

  return (
    <div className="rounded-xl border border-border bg-card overflow-hidden">
      <button
        onClick={() => setExpanded(!expanded)}
        className="flex items-center justify-between w-full px-4 py-3 text-left hover:bg-secondary/50 transition-colors"
      >
        <div className="flex items-center gap-3">
          <FileText className="h-5 w-5 text-muted-foreground" />
          <span className="text-sm font-medium">Audit Log</span>
          <span className="text-xs text-muted-foreground">
            Model: {log.modelName} | Latency: {log.latencyMs}ms
          </span>
        </div>
        {expanded ? (
          <ChevronUp className="h-4 w-4 text-muted-foreground" />
        ) : (
          <ChevronDown className="h-4 w-4 text-muted-foreground" />
        )}
      </button>

      {expanded && (
        <div className="border-t border-border px-4 py-3 space-y-3 text-xs">
          <div>
            <span className="font-medium text-muted-foreground">Timestamp:</span>{" "}
            <span className="text-foreground">
              {new Date(log.timestamp).toLocaleString()}
            </span>
          </div>
          <div>
            <span className="font-medium text-muted-foreground">Model:</span>{" "}
            <span className="text-foreground">{log.modelName}</span>
          </div>
          <div>
            <span className="font-medium text-muted-foreground">Safety checks run:</span>
            <div className="flex flex-wrap gap-1 mt-1">
              {log.safetyChecksRun?.map((check, i) => (
                <span
                  key={i}
                  className="rounded-full bg-secondary px-2 py-0.5 text-xs text-secondary-foreground"
                >
                  {check}
                </span>
              ))}
            </div>
          </div>
          <div>
            <span className="font-medium text-muted-foreground">Post-processing:</span>
            <div className="flex flex-wrap gap-1 mt-1">
              {log.postProcessingSteps?.map((step, i) => (
                <span
                  key={i}
                  className="rounded-full bg-secondary px-2 py-0.5 text-xs text-secondary-foreground"
                >
                  {step}
                </span>
              ))}
            </div>
          </div>
          <div>
            <span className="font-medium text-muted-foreground">Plan ID:</span>{" "}
            <span className="text-foreground font-mono">{log.planId}</span>
          </div>
        </div>
      )}
    </div>
  );
}
