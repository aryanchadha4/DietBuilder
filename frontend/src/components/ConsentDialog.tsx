"use client";

import { useState } from "react";
import { Button } from "./ui/Button";
import { Card, CardHeader, CardTitle } from "./ui/Card";
import { Shield, Lock, BarChart3, Users } from "lucide-react";

interface ConsentDialogProps {
  onConsent: (consents: Record<string, boolean>) => Promise<void>;
  loading?: boolean;
}

const CONSENT_OPTIONS = [
  {
    key: "data_storage",
    label: "Data Storage",
    description: "Store your profile and generated plans securely in our database",
    icon: Lock,
    required: true,
  },
  {
    key: "ai_processing",
    label: "AI Processing",
    description: "Use AI models to generate personalized diet recommendations based on your data",
    icon: Shield,
    required: true,
  },
  {
    key: "outcome_analytics",
    label: "Outcome Analytics",
    description: "Track your progress over time and use it to adapt future recommendations",
    icon: BarChart3,
    required: false,
  },
  {
    key: "fairness_research",
    label: "Fairness Research",
    description: "Use anonymized data to audit our system for demographic fairness",
    icon: Users,
    required: false,
  },
];

export function ConsentDialog({ onConsent, loading }: ConsentDialogProps) {
  const [consents, setConsents] = useState<Record<string, boolean>>({
    data_storage: true,
    ai_processing: true,
    outcome_analytics: false,
    fairness_research: false,
  });

  const canProceed = consents.data_storage && consents.ai_processing;

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 backdrop-blur-sm p-4">
      <Card className="max-w-lg w-full max-h-[90vh] overflow-y-auto">
        <CardHeader>
          <div className="flex items-center gap-3 mb-2">
            <Shield className="h-6 w-6 text-primary" />
            <CardTitle>Privacy & Consent</CardTitle>
          </div>
          <p className="text-sm text-muted-foreground">
            Your dietary and medical data is sensitive. We need your explicit consent
            before processing it. You can change these settings anytime.
          </p>
        </CardHeader>

        <div className="space-y-3 mt-2">
          {CONSENT_OPTIONS.map(({ key, label, description, icon: Icon, required }) => (
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
                disabled={required}
                className="h-4 w-4 rounded accent-primary mt-0.5"
              />
              <div className="flex-1">
                <div className="flex items-center gap-2">
                  <Icon className="h-4 w-4 text-muted-foreground" />
                  <span className="text-sm font-medium">{label}</span>
                  {required && (
                    <span className="text-xs text-muted-foreground">(required)</span>
                  )}
                </div>
                <p className="text-xs text-muted-foreground mt-0.5">{description}</p>
              </div>
            </label>
          ))}
        </div>

        <div className="mt-6 pt-4 border-t border-border">
          <Button
            className="w-full"
            onClick={() => onConsent(consents)}
            disabled={!canProceed}
            loading={loading}
          >
            Accept & Continue
          </Button>
          <p className="text-xs text-muted-foreground text-center mt-2">
            You can export or delete all your data at any time from Settings.
          </p>
        </div>
      </Card>
    </div>
  );
}
