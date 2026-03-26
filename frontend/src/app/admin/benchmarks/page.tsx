"use client";

import { useState } from "react";
import { api, BenchmarkResult } from "@/lib/api";
import { Card, CardHeader, CardTitle } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import toast from "react-hot-toast";
import { FlaskConical, CheckCircle2, XCircle } from "lucide-react";

export default function BenchmarksPage() {
  const [result, setResult] = useState<BenchmarkResult | null>(null);
  const [running, setRunning] = useState(false);

  async function runBenchmarks() {
    setRunning(true);
    try {
      const data = await api.admin.runBenchmarks();
      setResult(data);
      toast.success("Benchmarks complete");
    } catch {
      toast.error("Failed to run benchmarks");
    } finally {
      setRunning(false);
    }
  }

  return (
    <div className="mx-auto max-w-4xl px-4 sm:px-6 py-8 space-y-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold flex items-center gap-3">
            <FlaskConical className="h-8 w-8 text-primary" />
            Offline Benchmarks
          </h1>
          <p className="text-muted-foreground mt-1">
            Test safety flag detection against expert-written cases.
          </p>
        </div>
        <Button onClick={runBenchmarks} loading={running}>
          Run Benchmarks
        </Button>
      </div>

      {result && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
            <Card className="text-center">
              <p className="text-2xl font-bold">{result.totalCases}</p>
              <p className="text-xs text-muted-foreground">Total Cases</p>
            </Card>
            <Card className="text-center">
              <p className="text-2xl font-bold text-emerald-600">{result.passedCases}</p>
              <p className="text-xs text-muted-foreground">Passed</p>
            </Card>
            <Card className="text-center">
              <p className={`text-2xl font-bold ${result.passRate >= 90 ? "text-emerald-600" : result.passRate >= 70 ? "text-amber-600" : "text-red-600"}`}>
                {result.passRate}%
              </p>
              <p className="text-xs text-muted-foreground">Pass Rate</p>
            </Card>
            <Card className="text-center">
              <p className={`text-2xl font-bold ${result.safetyFlagRecall >= 90 ? "text-emerald-600" : "text-red-600"}`}>
                {result.safetyFlagRecall}%
              </p>
              <p className="text-xs text-muted-foreground">Safety Flag Recall</p>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>Case Results</CardTitle>
            </CardHeader>
            <div className="space-y-3">
              {result.caseResults.map((cr) => (
                <div
                  key={cr.caseName}
                  className={`flex items-start gap-3 rounded-xl border p-3 ${
                    cr.passed
                      ? "border-emerald-200 dark:border-emerald-800 bg-emerald-50/50 dark:bg-emerald-900/10"
                      : "border-red-200 dark:border-red-800 bg-red-50/50 dark:bg-red-900/10"
                  }`}
                >
                  {cr.passed ? (
                    <CheckCircle2 className="h-5 w-5 text-emerald-600 shrink-0 mt-0.5" />
                  ) : (
                    <XCircle className="h-5 w-5 text-red-600 shrink-0 mt-0.5" />
                  )}
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-sm font-medium">{cr.caseName}</span>
                      <Badge variant={cr.passed ? "success" : "destructive"}>
                        {cr.passed ? "PASS" : "FAIL"}
                      </Badge>
                    </div>
                    <div className="flex gap-4 mt-2 text-xs">
                      <div>
                        <span className="font-medium text-muted-foreground">Expected: </span>
                        {cr.expectedFlags.length > 0
                          ? cr.expectedFlags.join(", ")
                          : "none"}
                      </div>
                      <div>
                        <span className="font-medium text-muted-foreground">Actual: </span>
                        {cr.actualFlags.length > 0
                          ? cr.actualFlags.join(", ")
                          : "none"}
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </Card>
        </>
      )}
    </div>
  );
}
