"use client";

import { useState, useEffect } from "react";
import { api, UserProfile, ConsentRecord } from "@/lib/api";
import { Card, CardHeader, CardTitle } from "@/components/ui/Card";
import { Select } from "@/components/ui/Select";
import { Button } from "@/components/ui/Button";
import toast from "react-hot-toast";
import { Shield, Download, Trash2, Lock, BarChart3, Users, Cpu } from "lucide-react";

const CONSENT_OPTIONS = [
  { key: "data_storage", label: "Data Storage", icon: Lock, description: "Store your profile and generated plans securely" },
  { key: "ai_processing", label: "AI Processing", icon: Cpu, description: "Use AI models to generate personalized recommendations" },
  { key: "outcome_analytics", label: "Outcome Analytics", icon: BarChart3, description: "Track progress and use it to adapt plans" },
  { key: "fairness_research", label: "Fairness Research", icon: Users, description: "Use anonymized data for fairness audits" },
];

export default function PrivacySettingsPage() {
  const [profiles, setProfiles] = useState<UserProfile[]>([]);
  const [selectedProfile, setSelectedProfile] = useState("");
  const [consent, setConsent] = useState<ConsentRecord | null>(null);
  const [consents, setConsents] = useState<Record<string, boolean>>({
    data_storage: false,
    ai_processing: false,
    outcome_analytics: false,
    fairness_research: false,
  });
  const [saving, setSaving] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  useEffect(() => {
    api.profiles.list().then(setProfiles).catch(() => {});
  }, []);

  useEffect(() => {
    if (selectedProfile) {
      api.privacy.getConsent(selectedProfile).then((c) => {
        setConsent(c);
        setConsents(c.consents || {});
      }).catch(() => {
        setConsent(null);
        setConsents({
          data_storage: false,
          ai_processing: false,
          outcome_analytics: false,
          fairness_research: false,
        });
      });
    }
  }, [selectedProfile]);

  async function handleSaveConsent() {
    if (!selectedProfile) return;
    setSaving(true);
    try {
      const updated = await api.privacy.setConsent(selectedProfile, consents);
      setConsent(updated);
      toast.success("Privacy settings saved");
    } catch {
      toast.error("Failed to save settings");
    } finally {
      setSaving(false);
    }
  }

  async function handleExport() {
    if (!selectedProfile) return;
    try {
      const data = await api.privacy.exportData(selectedProfile);
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `dietbuilder-export-${selectedProfile}.json`;
      a.click();
      URL.revokeObjectURL(url);
      toast.success("Data exported");
    } catch {
      toast.error("Failed to export data");
    }
  }

  async function handleDelete() {
    if (!selectedProfile) return;
    try {
      await api.privacy.deleteData(selectedProfile);
      toast.success("All data deleted");
      setShowDeleteConfirm(false);
      setSelectedProfile("");
      api.profiles.list().then(setProfiles).catch(() => {});
    } catch {
      toast.error("Failed to delete data");
    }
  }

  return (
    <div className="mx-auto max-w-2xl px-4 sm:px-6 py-8 space-y-8">
      <div>
        <h1 className="text-3xl font-bold flex items-center gap-3">
          <Shield className="h-8 w-8 text-primary" />
          Privacy Settings
        </h1>
        <p className="text-muted-foreground mt-1">
          Control how your data is stored, processed, and used.
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
          {/* Consent toggles */}
          <Card>
            <CardHeader>
              <CardTitle>Consent Preferences</CardTitle>
            </CardHeader>
            <div className="space-y-3">
              {CONSENT_OPTIONS.map(({ key, label, icon: Icon, description }) => (
                <label
                  key={key}
                  className="flex items-start gap-3 rounded-xl border border-border p-3 cursor-pointer hover:bg-secondary/50 transition-colors"
                >
                  <input
                    type="checkbox"
                    checked={consents[key] ?? false}
                    onChange={(e) =>
                      setConsents((prev) => ({ ...prev, [key]: e.target.checked }))
                    }
                    className="h-4 w-4 rounded accent-primary mt-0.5"
                  />
                  <div className="flex-1">
                    <div className="flex items-center gap-2">
                      <Icon className="h-4 w-4 text-muted-foreground" />
                      <span className="text-sm font-medium">{label}</span>
                    </div>
                    <p className="text-xs text-muted-foreground mt-0.5">{description}</p>
                  </div>
                </label>
              ))}
            </div>
            <Button className="w-full mt-4" onClick={handleSaveConsent} loading={saving}>
              Save Preferences
            </Button>
            {consent?.grantedAt && (
              <p className="text-xs text-muted-foreground text-center mt-2">
                Last updated: {new Date(consent.grantedAt).toLocaleString()}
              </p>
            )}
          </Card>

          {/* Data actions */}
          <Card>
            <CardHeader>
              <CardTitle>Your Data</CardTitle>
            </CardHeader>
            <div className="space-y-3">
              <Button variant="secondary" className="w-full" onClick={handleExport}>
                <Download className="h-4 w-4 mr-2" />
                Export All Data (JSON)
              </Button>
              {!showDeleteConfirm ? (
                <Button
                  variant="ghost"
                  className="w-full text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20"
                  onClick={() => setShowDeleteConfirm(true)}
                >
                  <Trash2 className="h-4 w-4 mr-2" />
                  Delete All Data
                </Button>
              ) : (
                <div className="rounded-xl border border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/20 p-4 space-y-3">
                  <p className="text-sm font-medium text-red-800 dark:text-red-300">
                    This will permanently delete all your data including profiles, plans, outcomes, and logs.
                    This action cannot be undone.
                  </p>
                  <div className="flex gap-3">
                    <Button
                      variant="ghost"
                      onClick={() => setShowDeleteConfirm(false)}
                      className="flex-1"
                    >
                      Cancel
                    </Button>
                    <Button
                      className="flex-1 bg-red-600 hover:bg-red-700 text-white"
                      onClick={handleDelete}
                    >
                      Confirm Delete
                    </Button>
                  </div>
                </div>
              )}
            </div>
          </Card>
        </>
      )}
    </div>
  );
}
