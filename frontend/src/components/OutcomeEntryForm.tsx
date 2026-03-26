"use client";

import { useState } from "react";
import { Input } from "./ui/Input";
import { Textarea } from "./ui/Textarea";
import { Button } from "./ui/Button";
import { Select } from "./ui/Select";
import { OutcomeRecord } from "@/lib/api";
import { Plus, X } from "lucide-react";

interface OutcomeEntryFormProps {
  profileId: string;
  planId?: string;
  onSubmit: (record: Partial<OutcomeRecord>) => Promise<void>;
  loading?: boolean;
}

const TRAINING_OPTIONS = [
  { value: "", label: "Select..." },
  { value: "improving", label: "Improving" },
  { value: "plateau", label: "Plateau" },
  { value: "regressing", label: "Regressing" },
];

const COMMON_SYMPTOMS = [
  "Fatigue", "Bloating", "Headache", "Nausea", "Constipation",
  "Diarrhea", "Insomnia", "Cravings", "Dizziness", "Joint pain",
];

export function OutcomeEntryForm({
  profileId,
  planId,
  onSubmit,
  loading,
}: OutcomeEntryFormProps) {
  const [weight, setWeight] = useState("");
  const [adherence, setAdherence] = useState("");
  const [symptoms, setSymptoms] = useState<string[]>([]);
  const [trainingResponse, setTrainingResponse] = useState("");
  const [notes, setNotes] = useState("");
  const [labName, setLabName] = useState("");
  const [labValue, setLabValue] = useState("");
  const [labs, setLabs] = useState<Record<string, number>>({});

  function addSymptom(symptom: string) {
    if (!symptoms.includes(symptom)) {
      setSymptoms([...symptoms, symptom]);
    }
  }

  function addLab() {
    if (labName && labValue) {
      setLabs({ ...labs, [labName]: parseFloat(labValue) });
      setLabName("");
      setLabValue("");
    }
  }

  async function handleSubmit() {
    const record: Partial<OutcomeRecord> = {
      profileId,
      planId,
      weightKg: weight ? parseFloat(weight) : undefined,
      adherencePercent: adherence ? parseInt(adherence) : undefined,
      symptoms: symptoms.length > 0 ? symptoms : undefined,
      labResults: Object.keys(labs).length > 0 ? labs : undefined,
      trainingResponse: trainingResponse || undefined,
      notes: notes || undefined,
    };
    await onSubmit(record);
    setWeight("");
    setAdherence("");
    setSymptoms([]);
    setTrainingResponse("");
    setNotes("");
    setLabs({});
  }

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <Input
          label="Weight (kg)"
          type="number"
          value={weight}
          onChange={(e) => setWeight(e.target.value)}
          placeholder="75.0"
        />
        <Input
          label="Adherence (%)"
          type="number"
          value={adherence}
          onChange={(e) => setAdherence(e.target.value)}
          placeholder="80"
        />
      </div>

      <Select
        label="Training Response"
        value={trainingResponse}
        onChange={(e) => setTrainingResponse(e.target.value)}
        options={TRAINING_OPTIONS}
      />

      <div>
        <label className="block text-sm font-medium text-foreground mb-1.5">
          Symptoms
        </label>
        <div className="flex flex-wrap gap-1.5 mb-2">
          {COMMON_SYMPTOMS.filter((s) => !symptoms.includes(s)).map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => addSymptom(s)}
              className="rounded-full border border-border px-2.5 py-0.5 text-xs text-muted-foreground hover:bg-secondary hover:text-foreground transition-colors"
            >
              + {s}
            </button>
          ))}
        </div>
        <div className="flex flex-wrap gap-2">
          {symptoms.map((s, i) => (
            <span
              key={i}
              className="inline-flex items-center gap-1 rounded-full bg-red-100 dark:bg-red-900/30 px-3 py-1 text-xs text-red-700 dark:text-red-300"
            >
              {s}
              <button onClick={() => setSymptoms(symptoms.filter((_, j) => j !== i))}>
                <X className="h-3 w-3" />
              </button>
            </span>
          ))}
        </div>
      </div>

      <div>
        <label className="block text-sm font-medium text-foreground mb-1.5">
          Lab Results (optional)
        </label>
        <div className="flex gap-2">
          <Input
            value={labName}
            onChange={(e) => setLabName(e.target.value)}
            placeholder="e.g., HbA1c"
          />
          <Input
            type="number"
            value={labValue}
            onChange={(e) => setLabValue(e.target.value)}
            placeholder="Value"
          />
          <Button size="md" variant="secondary" onClick={addLab}>
            <Plus className="h-4 w-4" />
          </Button>
        </div>
        {Object.keys(labs).length > 0 && (
          <div className="flex flex-wrap gap-2 mt-2">
            {Object.entries(labs).map(([name, value]) => (
              <span
                key={name}
                className="inline-flex items-center gap-1 rounded-full bg-blue-100 dark:bg-blue-900/30 px-3 py-1 text-xs text-blue-700 dark:text-blue-300"
              >
                {name}: {value}
                <button onClick={() => {
                  const next = { ...labs };
                  delete next[name];
                  setLabs(next);
                }}>
                  <X className="h-3 w-3" />
                </button>
              </span>
            ))}
          </div>
        )}
      </div>

      <Textarea
        label="Notes"
        value={notes}
        onChange={(e) => setNotes(e.target.value)}
        placeholder="Any additional observations..."
        rows={2}
      />

      <Button onClick={handleSubmit} loading={loading} className="w-full">
        Record Outcome
      </Button>
    </div>
  );
}
