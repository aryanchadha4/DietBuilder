"use client";

import { EvidenceLevel, EvidenceTag } from "@/lib/api";
import { useState } from "react";
import { ExternalLink } from "lucide-react";

const LEVEL_CONFIG: Record<
  EvidenceLevel,
  { label: string; bg: string; text: string }
> = {
  GUIDELINE_BACKED: {
    label: "Guideline",
    bg: "bg-emerald-100 dark:bg-emerald-900/30",
    text: "text-emerald-800 dark:text-emerald-300",
  },
  META_ANALYSIS: {
    label: "Meta-analysis",
    bg: "bg-blue-100 dark:bg-blue-900/30",
    text: "text-blue-800 dark:text-blue-300",
  },
  OBSERVATIONAL: {
    label: "Observational",
    bg: "bg-amber-100 dark:bg-amber-900/30",
    text: "text-amber-800 dark:text-amber-300",
  },
  LOW_CONFIDENCE: {
    label: "Low confidence",
    bg: "bg-gray-100 dark:bg-gray-800/50",
    text: "text-gray-600 dark:text-gray-400",
  },
};

export function EvidenceBadge({ tag }: { tag: EvidenceTag }) {
  const [showTooltip, setShowTooltip] = useState(false);
  const config = LEVEL_CONFIG[tag.level] || LEVEL_CONFIG.LOW_CONFIDENCE;

  return (
    <span className="relative inline-block">
      <button
        onMouseEnter={() => setShowTooltip(true)}
        onMouseLeave={() => setShowTooltip(false)}
        onClick={() => setShowTooltip(!showTooltip)}
        className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium ${config.bg} ${config.text} transition-colors`}
      >
        {config.label}
        {tag.sourceId ? <span className="text-[10px] opacity-80">cite</span> : null}
      </button>
      {showTooltip && (
        <div className="absolute z-50 bottom-full left-1/2 -translate-x-1/2 mb-2 w-72 rounded-xl border border-border bg-card p-3 shadow-lg text-xs">
          <p className="font-medium text-foreground mb-1">{tag.claim}</p>
          <p className="text-muted-foreground mb-1">{tag.explanation}</p>
          <p className="text-muted-foreground/70 italic">Source: {tag.citationText || tag.source}</p>
          {tag.simpleSummary && (
            <p className="text-muted-foreground mt-1">{tag.simpleSummary}</p>
          )}
          {(tag.url || tag.doi) && (
            <div className="mt-2">
              <a
                href={tag.url || `https://doi.org/${tag.doi}`}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-1 text-primary hover:underline"
              >
                View reference <ExternalLink className="h-3 w-3" />
              </a>
            </div>
          )}
          <div className="absolute top-full left-1/2 -translate-x-1/2 -mt-px border-4 border-transparent border-t-border" />
        </div>
      )}
    </span>
  );
}

export function EvidenceTagList({ tags }: { tags?: EvidenceTag[] }) {
  if (!tags || tags.length === 0) return null;
  return (
    <div className="flex flex-wrap gap-2">
      {tags.map((tag, i) => (
        <EvidenceBadge key={i} tag={tag} />
      ))}
    </div>
  );
}
