"use client";

import { ExpertSource } from "@/lib/api";
import { Card, CardHeader, CardTitle } from "./ui/Card";
import { Badge } from "./ui/Badge";
import { useMemo, useState } from "react";

interface CitationPanelProps {
  sources?: ExpertSource[];
}

export function CitationPanel({ sources }: CitationPanelProps) {
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const sorted = useMemo(
    () =>
      [...(sources || [])].sort(
        (a, b) => (b.credibilityScore || 0) - (a.credibilityScore || 0)
      ),
    [sources]
  );

  if (!sorted.length) return null;

  return (
    <Card>
      <CardHeader>
        <CardTitle>Supporting References</CardTitle>
      </CardHeader>
      <div className="space-y-3">
        {sorted.map((source) => (
          <div key={source.id} className="rounded-lg border border-border p-3">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-sm font-medium">{source.title}</p>
                <p className="text-xs text-muted-foreground">
                  {source.authors || source.organization || "Unknown source"}
                  {source.publicationDate ? ` • ${source.publicationDate}` : ""}
                </p>
              </div>
              <div className="flex items-center gap-2">
                {source.sourceType && <Badge variant="default">{source.sourceType}</Badge>}
                <Badge variant="info">
                  {Math.round((source.credibilityScore || 0) * 100)}%
                </Badge>
              </div>
            </div>

            <div className="mt-2 flex items-center gap-3 text-xs">
              <button
                className="text-primary hover:underline"
                onClick={() =>
                  setExpanded((prev) => ({ ...prev, [source.id || "unknown"]: !prev[source.id || "unknown"] }))
                }
              >
                {expanded[source.id || "unknown"] ? "Hide summary" : "View summary"}
              </button>
              {source.url && (
                <a
                  href={source.url}
                  target="_blank"
                  rel="noreferrer"
                  className="text-primary hover:underline"
                >
                  View full reference
                </a>
              )}
            </div>

            {expanded[source.id || "unknown"] && (
              <div className="mt-2 space-y-2">
                {source.summary && (
                  <p className="text-xs text-muted-foreground">{source.summary}</p>
                )}
                {!!source.keyFindings?.length && (
                  <ul className="list-disc pl-5 text-xs text-muted-foreground">
                    {source.keyFindings.map((finding, i) => (
                      <li key={i}>{finding}</li>
                    ))}
                  </ul>
                )}
              </div>
            )}
          </div>
        ))}
      </div>
    </Card>
  );
}
