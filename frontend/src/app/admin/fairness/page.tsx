"use client";

import { useState, useEffect } from "react";
import { api, FairnessReport, GroupMetrics } from "@/lib/api";
import { Card, CardHeader, CardTitle } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import toast from "react-hot-toast";
import { Users, AlertTriangle, BarChart3 } from "lucide-react";

function MetricsTable({ title, metrics }: { title: string; metrics?: Record<string, GroupMetrics> }) {
  if (!metrics || Object.keys(metrics).length === 0) return null;
  return (
    <div>
      <h3 className="text-sm font-medium mb-2">{title}</h3>
      <div className="overflow-x-auto">
        <table className="w-full text-xs">
          <thead>
            <tr className="border-b border-border">
              <th className="text-left py-2 pr-4">Group</th>
              <th className="text-right py-2 px-2">N</th>
              <th className="text-right py-2 px-2">Adequacy</th>
              <th className="text-right py-2 px-2">Avg Calories</th>
              <th className="text-right py-2 px-2">Alert Rate</th>
            </tr>
          </thead>
          <tbody>
            {Object.entries(metrics).map(([group, m]) => (
              <tr key={group} className="border-b border-border/50">
                <td className="py-2 pr-4 font-medium">{group}</td>
                <td className="text-right py-2 px-2 text-muted-foreground">{m.count}</td>
                <td className="text-right py-2 px-2">
                  <span className={m.avgAdequacyScore >= 70 ? "text-emerald-600" : "text-amber-600"}>
                    {m.avgAdequacyScore.toFixed(1)}%
                  </span>
                </td>
                <td className="text-right py-2 px-2 text-muted-foreground">
                  {Math.round(m.avgCalorieAccuracy)}
                </td>
                <td className="text-right py-2 px-2 text-muted-foreground">
                  {m.safetyAlertRate.toFixed(1)}%
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default function FairnessPage() {
  const [reports, setReports] = useState<FairnessReport[]>([]);
  const [running, setRunning] = useState(false);
  const [selectedReport, setSelectedReport] = useState<FairnessReport | null>(null);

  useEffect(() => {
    api.admin.fairnessReports().then(setReports).catch(() => {});
  }, []);

  async function runAudit() {
    setRunning(true);
    try {
      const report = await api.admin.runFairness();
      setReports([report, ...reports]);
      setSelectedReport(report);
      toast.success("Fairness audit complete");
    } catch {
      toast.error("Failed to run fairness audit");
    } finally {
      setRunning(false);
    }
  }

  const report = selectedReport || reports[0];

  return (
    <div className="mx-auto max-w-5xl px-4 sm:px-6 py-8 space-y-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold flex items-center gap-3">
            <Users className="h-8 w-8 text-primary" />
            Fairness Audits
          </h1>
          <p className="text-muted-foreground mt-1">
            Monitor plan quality across demographic groups.
          </p>
        </div>
        <Button onClick={runAudit} loading={running}>
          <BarChart3 className="h-4 w-4 mr-2" />
          Run Audit
        </Button>
      </div>

      {report && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <Card className="text-center">
              <p className="text-2xl font-bold">{report.totalPlansAnalyzed}</p>
              <p className="text-xs text-muted-foreground">Plans Analyzed</p>
            </Card>
            <Card className="text-center">
              <p className="text-2xl font-bold">{report.flaggedDisparities?.length || 0}</p>
              <p className="text-xs text-muted-foreground">Disparities Flagged</p>
            </Card>
            <Card className="text-center">
              <p className="text-xs text-muted-foreground">
                Generated: {new Date(report.generatedAt).toLocaleString()}
              </p>
            </Card>
          </div>

          {report.flaggedDisparities && report.flaggedDisparities.length > 0 && (
            <Card className="border-red-200 dark:border-red-800">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-red-700 dark:text-red-400">
                  <AlertTriangle className="h-5 w-5" />
                  Flagged Disparities
                </CardTitle>
              </CardHeader>
              <div className="space-y-2">
                {report.flaggedDisparities.map((d, i) => (
                  <div key={i} className="flex items-start gap-2 text-sm">
                    <Badge variant="destructive" className="shrink-0">Flag</Badge>
                    <span className="text-muted-foreground">{d}</span>
                  </div>
                ))}
              </div>
            </Card>
          )}

          <Card>
            <CardHeader><CardTitle>Group Breakdowns</CardTitle></CardHeader>
            <div className="space-y-6">
              <MetricsTable title="By Gender" metrics={report.byGender} />
              <MetricsTable title="By Age Group" metrics={report.byAgeGroup} />
              <MetricsTable title="By Culture" metrics={report.byCulture} />
            </div>
          </Card>
        </>
      )}

      {reports.length > 1 && (
        <div className="space-y-2">
          <h2 className="text-lg font-semibold">Previous Reports</h2>
          {reports.slice(1).map((r) => (
            <button
              key={r.id}
              onClick={() => setSelectedReport(r)}
              className="w-full text-left rounded-xl border border-border p-3 hover:bg-secondary/50 transition-colors text-sm"
            >
              <span className="font-medium">
                {new Date(r.generatedAt).toLocaleDateString()}
              </span>
              <span className="ml-3 text-muted-foreground">
                {r.totalPlansAnalyzed} plans | {r.flaggedDisparities?.length || 0} disparities
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
