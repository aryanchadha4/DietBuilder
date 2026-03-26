"use client";

import { OutcomeRecord } from "@/lib/api";

interface WeightChartProps {
  outcomes: OutcomeRecord[];
}

export function WeightChart({ outcomes }: WeightChartProps) {
  const withWeight = outcomes
    .filter((o) => o.weightKg != null)
    .sort((a, b) => (a.recordedAt || "").localeCompare(b.recordedAt || ""));

  if (withWeight.length < 2) {
    return (
      <div className="text-sm text-muted-foreground text-center py-8">
        Need at least 2 weight entries to show chart
      </div>
    );
  }

  const weights = withWeight.map((o) => o.weightKg!);
  const minW = Math.min(...weights) - 2;
  const maxW = Math.max(...weights) + 2;
  const range = maxW - minW || 1;

  const chartWidth = 500;
  const chartHeight = 200;
  const padX = 50;
  const padY = 20;
  const plotW = chartWidth - padX * 2;
  const plotH = chartHeight - padY * 2;

  const points = withWeight.map((o, i) => {
    const x = padX + (i / (withWeight.length - 1)) * plotW;
    const y = padY + plotH - ((o.weightKg! - minW) / range) * plotH;
    return { x, y, weight: o.weightKg!, date: o.recordedAt };
  });

  const pathD = points.map((p, i) => `${i === 0 ? "M" : "L"} ${p.x} ${p.y}`).join(" ");

  return (
    <div className="w-full overflow-x-auto">
      <svg
        viewBox={`0 0 ${chartWidth} ${chartHeight}`}
        className="w-full max-w-lg mx-auto"
        preserveAspectRatio="xMidYMid meet"
      >
        {/* Grid lines */}
        {[0, 0.25, 0.5, 0.75, 1].map((frac) => {
          const y = padY + plotH - frac * plotH;
          const val = (minW + frac * range).toFixed(1);
          return (
            <g key={frac}>
              <line
                x1={padX}
                y1={y}
                x2={chartWidth - padX}
                y2={y}
                stroke="var(--border)"
                strokeWidth="0.5"
                strokeDasharray="4 4"
              />
              <text
                x={padX - 6}
                y={y + 4}
                textAnchor="end"
                className="text-[10px] fill-[var(--muted-foreground)]"
              >
                {val}
              </text>
            </g>
          );
        })}

        {/* Line */}
        <path d={pathD} fill="none" stroke="var(--primary)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />

        {/* Data points */}
        {points.map((p, i) => (
          <g key={i}>
            <circle cx={p.x} cy={p.y} r="4" fill="var(--primary)" />
            <circle cx={p.x} cy={p.y} r="6" fill="var(--primary)" opacity="0.2" />
            {withWeight.length <= 12 && (
              <text
                x={p.x}
                y={chartHeight - 4}
                textAnchor="middle"
                className="text-[8px] fill-[var(--muted-foreground)]"
              >
                {p.date ? new Date(p.date).toLocaleDateString(undefined, { month: "short", day: "numeric" }) : ""}
              </text>
            )}
          </g>
        ))}

        {/* Axis label */}
        <text
          x={padX - 6}
          y={padY - 6}
          textAnchor="end"
          className="text-[9px] fill-[var(--muted-foreground)]"
        >
          kg
        </text>
      </svg>
    </div>
  );
}
