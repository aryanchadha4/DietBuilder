"use client";

import { useState, useEffect, useCallback } from "react";
import { api, UserProfile, OutcomeRecord, OutcomeTrends, AdaptationAssessment } from "@/lib/api";
import { formatWeight } from "@/lib/units";
import { Card, CardHeader, CardTitle } from "@/components/ui/Card";
import { Select } from "@/components/ui/Select";
import { Button } from "@/components/ui/Button";
import { OutcomeEntryForm } from "@/components/OutcomeEntryForm";
import { WeightChart } from "@/components/WeightChart";
import { Badge } from "@/components/ui/Badge";
import { EmptyState } from "@/components/ui/EmptyState";
import toast from "react-hot-toast";
import { Activity, TrendingDown, TrendingUp, Minus, RefreshCw } from "lucide-react";

export default function OutcomesPage() {
  const [profiles, setProfiles] = useState<UserProfile[]>([]);
  const [selectedProfile, setSelectedProfile] = useState("");
  const [outcomes, setOutcomes] = useState<OutcomeRecord[]>([]);
  const [trends, setTrends] = useState<OutcomeTrends | null>(null);
  const [adaptation, setAdaptation] = useState<AdaptationAssessment | null>(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    api.profiles.list().then(setProfiles).catch(() => {});
  }, []);

  const loadOutcomeData = useCallback(async () => {
    if (!selectedProfile) return;
    setLoading(true);
    try {
      const [outcomesData, trendsData, adaptData] = await Promise.all([
        api.outcomes.list(selectedProfile),
        api.outcomes.trends(selectedProfile),
        api.outcomes.checkAdaptation(selectedProfile),
      ]);
      setOutcomes(outcomesData);
      setTrends(trendsData);
      setAdaptation(adaptData);
    } catch {
      toast.error("Failed to load outcome data");
    } finally {
      setLoading(false);
    }
  }, [selectedProfile]);

  useEffect(() => {
    if (selectedProfile) {
      void loadOutcomeData();
    }
  }, [selectedProfile, loadOutcomeData]);

  async function handleSubmitOutcome(record: Partial<OutcomeRecord>) {
    setSubmitting(true);
    try {
      await api.outcomes.record(record);
      toast.success("Outcome recorded");
      loadOutcomeData();
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "Failed to record outcome");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleAdaptPlan() {
    if (!selectedProfile) return;
    setLoading(true);
    try {
      const plan = await api.outcomes.adaptPlan(selectedProfile);
      toast.success(`Adapted plan generated (v${plan.version})`);
      loadOutcomeData();
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "Failed to adapt plan");
    } finally {
      setLoading(false);
    }
  }

  const currentProfile = profiles.find((p) => p.id === selectedProfile);
  const units = currentProfile?.preferredUnits;

  const DirectionIcon = trends && trends.weightChangeKg < -0.1
    ? TrendingDown
    : trends && trends.weightChangeKg > 0.1
    ? TrendingUp
    : Minus;

  return (
    <div className="mx-auto max-w-4xl px-4 sm:px-6 py-8 space-y-8">
      <div>
        <h1 className="text-3xl font-bold">Outcome Tracking</h1>
        <p className="text-muted-foreground mt-1">
          Log your progress and let the system adapt your plan.
        </p>
      </div>

      <Select
        label="Select Profile"
        value={selectedProfile}
        onChange={(e) => setSelectedProfile(e.target.value)}
        options={[
          { value: "", label: "Choose a profile..." },
          ...profiles.map((p) => ({ value: p.id!, label: p.name })),
        ]}
      />

      {selectedProfile && (
        <>
          {/* Trends summary */}
          {trends && trends.totalRecords > 0 && (
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <Card className="text-center">
                <DirectionIcon className="h-6 w-6 mx-auto text-primary mb-1" />
                <p className="text-lg font-bold">
                  {trends.weightChangeKg > 0 ? "+" : ""}
                  {units === "IMPERIAL"
                    ? `${(trends.weightChangeKg / 0.453592).toFixed(1)} lbs`
                    : `${trends.weightChangeKg.toFixed(1)} kg`}
                </p>
                <p className="text-xs text-muted-foreground">Weight Change</p>
              </Card>
              <Card className="text-center">
                <p className="text-lg font-bold">{Math.round(trends.avgAdherence)}%</p>
                <p className="text-xs text-muted-foreground">Avg Adherence</p>
              </Card>
              <Card className="text-center">
                <p className="text-lg font-bold">{trends.totalRecords}</p>
                <p className="text-xs text-muted-foreground">Total Records</p>
              </Card>
            </div>
          )}

          {/* Weight chart */}
          {outcomes.length >= 2 && (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Activity className="h-5 w-5 text-primary" />
                  Weight Trend
                </CardTitle>
              </CardHeader>
              <WeightChart outcomes={outcomes} />
            </Card>
          )}

          {/* Adaptation assessment */}
          {adaptation && adaptation.adaptRecommended && (
            <Card className="border-primary/50">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <RefreshCw className="h-5 w-5 text-primary" />
                  Plan Adaptation Recommended
                </CardTitle>
              </CardHeader>
              <div className="space-y-2 mb-4">
                {adaptation.reasons.map((reason, i) => (
                  <div key={i} className="flex items-start gap-2 text-sm">
                    <Badge variant="warning" className="shrink-0 mt-0.5">Reason</Badge>
                    <span className="text-muted-foreground">{reason}</span>
                  </div>
                ))}
              </div>
              <Button onClick={handleAdaptPlan} loading={loading}>
                Generate Adapted Plan
              </Button>
            </Card>
          )}

          {/* Record new outcome */}
          <Card>
            <CardHeader>
              <CardTitle>Record Outcome</CardTitle>
            </CardHeader>
            <OutcomeEntryForm
              profileId={selectedProfile}
              onSubmit={handleSubmitOutcome}
              loading={submitting}
            />
          </Card>

          {/* History */}
          {outcomes.length > 0 ? (
            <div className="space-y-3">
              <h2 className="text-xl font-semibold">Outcome History</h2>
              {outcomes.map((o) => (
                <Card key={o.id} className="text-sm">
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-medium">
                      {o.recordedAt ? new Date(o.recordedAt).toLocaleDateString() : "Unknown date"}
                    </span>
                    <div className="flex gap-2">
                      {o.weightKg && <Badge>{formatWeight(o.weightKg, units)}</Badge>}
                      {o.adherencePercent != null && <Badge variant="info">{o.adherencePercent}%</Badge>}
                    </div>
                  </div>
                  {o.symptoms && o.symptoms.length > 0 && (
                    <div className="flex flex-wrap gap-1 mb-1">
                      {o.symptoms.map((s, i) => (
                        <span key={i} className="rounded-full bg-red-100 dark:bg-red-900/30 px-2 py-0.5 text-xs text-red-700 dark:text-red-300">
                          {s}
                        </span>
                      ))}
                    </div>
                  )}
                  {o.notes && <p className="text-muted-foreground text-xs">{o.notes}</p>}
                </Card>
              ))}
            </div>
          ) : (
            <EmptyState
              icon={Activity}
              title="No outcomes yet"
              description="Start recording your progress above."
            />
          )}
        </>
      )}
    </div>
  );
}
