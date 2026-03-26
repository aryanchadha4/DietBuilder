"use client";

import { useState, useEffect } from "react";
import { Input } from "./ui/Input";
import { Select } from "./ui/Select";
import { Textarea } from "./ui/Textarea";
import { Button } from "./ui/Button";
import { Card } from "./ui/Card";
import { UserProfile, ExerciseSchedule } from "@/lib/api";
import { cmToFtIn, ftInToCm, kgToLbs, lbsToKg } from "@/lib/units";
import { ChevronLeft, ChevronRight, Check, Plus, Trash2 } from "lucide-react";

const STEPS = [
  "Personal Info",
  "Exercise",
  "Medical & Diet",
  "Goals & Preferences",
];

const GENDER_OPTIONS = [
  { value: "", label: "Select gender" },
  { value: "Male", label: "Male" },
  { value: "Female", label: "Female" },
  { value: "Non-binary", label: "Non-binary" },
  { value: "Prefer not to say", label: "Prefer not to say" },
];

const RACE_OPTIONS = [
  { value: "", label: "Select race/ethnicity" },
  { value: "Asian", label: "Asian" },
  { value: "Black or African American", label: "Black or African American" },
  { value: "Hispanic or Latino", label: "Hispanic or Latino" },
  { value: "White", label: "White" },
  { value: "Native American", label: "Native American" },
  { value: "Pacific Islander", label: "Pacific Islander" },
  { value: "Middle Eastern", label: "Middle Eastern" },
  { value: "Mixed/Multiracial", label: "Mixed / Multiracial" },
  { value: "Other", label: "Other" },
];

interface ProfileFormProps {
  initialData?: UserProfile;
  onSubmit: (profile: UserProfile) => Promise<void>;
  loading?: boolean;
}

function emptyExercise(): ExerciseSchedule {
  return { daysPerWeek: 0, type: "", durationMinutes: 0 };
}

function emptyProfile(): UserProfile {
  return {
    name: "",
    age: 0,
    gender: "",
    race: "",
    heightCm: 0,
    weightKg: 0,
    preferredUnits: "METRIC",
    strengthTraining: [emptyExercise()],
    cardioSchedule: [emptyExercise()],
    dietaryRestrictions: [],
    medicalInfo: "",
    goals: [],
    availableFoods: [],
  };
}

function TagInput({
  label,
  items,
  onAdd,
  onRemove,
  placeholder,
  suggestions,
}: {
  label: string;
  items: string[];
  onAdd: (v: string) => void;
  onRemove: (i: number) => void;
  placeholder: string;
  suggestions?: string[];
}) {
  const [input, setInput] = useState("");
  const unusedSuggestions = suggestions?.filter(
    (s) => !items.some((it) => it.toLowerCase() === s.toLowerCase())
  );

  return (
    <div>
      <label className="block text-sm font-medium text-foreground mb-1.5">
        {label}
      </label>
      <div className="flex gap-2">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder={placeholder}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              if (input.trim()) { onAdd(input.trim()); setInput(""); }
            }
          }}
          className="flex-1 rounded-xl border border-border bg-card px-4 py-2.5 text-sm text-foreground placeholder:text-muted-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-ring/20"
        />
        <Button size="md" onClick={() => { if (input.trim()) { onAdd(input.trim()); setInput(""); } }}>
          Add
        </Button>
      </div>
      {unusedSuggestions && unusedSuggestions.length > 0 && (
        <div className="flex flex-wrap gap-1.5 mt-2">
          {unusedSuggestions.map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => onAdd(s)}
              className="rounded-full border border-border px-2.5 py-0.5 text-xs text-muted-foreground hover:bg-secondary hover:text-foreground transition-colors"
            >
              + {s}
            </button>
          ))}
        </div>
      )}
      <div className="flex flex-wrap gap-2 mt-2">
        {items.map((item, i) => (
          <span key={i} className="inline-flex items-center gap-1 rounded-full bg-primary/10 text-primary px-3 py-1 text-sm">
            {item}
            <button onClick={() => onRemove(i)} className="hover:text-destructive">&times;</button>
          </span>
        ))}
      </div>
    </div>
  );
}

function ExerciseEntryList({
  label,
  entries,
  onChange,
}: {
  label: string;
  entries: ExerciseSchedule[];
  onChange: (entries: ExerciseSchedule[]) => void;
}) {
  function updateEntry(index: number, field: keyof ExerciseSchedule, value: string | number) {
    const updated = [...entries];
    updated[index] = { ...updated[index], [field]: value };
    onChange(updated);
  }

  function addEntry() {
    onChange([...entries, emptyExercise()]);
  }

  function removeEntry(index: number) {
    if (entries.length <= 1) {
      onChange([emptyExercise()]);
      return;
    }
    onChange(entries.filter((_, i) => i !== index));
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-medium text-foreground">{label}</h3>
        <Button variant="ghost" size="sm" onClick={addEntry}>
          <Plus className="h-3.5 w-3.5 mr-1" /> Add
        </Button>
      </div>
      <div className="space-y-3">
        {entries.map((entry, i) => (
          <div key={i} className="flex items-end gap-2 p-3 rounded-lg bg-secondary/30 border border-border/50">
            <div className="flex-1 grid grid-cols-1 sm:grid-cols-3 gap-3">
              <Input
                label="Type"
                value={entry.type}
                onChange={(e) => updateEntry(i, "type", e.target.value)}
                placeholder={label.includes("Strength") ? "e.g., Powerlifting" : "e.g., Running"}
              />
              <Input
                label="Days / week"
                type="number"
                value={entry.daysPerWeek || ""}
                onChange={(e) => updateEntry(i, "daysPerWeek", parseInt(e.target.value) || 0)}
                placeholder="3"
              />
              <Input
                label="Duration (min)"
                type="number"
                value={entry.durationMinutes || ""}
                onChange={(e) => updateEntry(i, "durationMinutes", parseInt(e.target.value) || 0)}
                placeholder="60"
              />
            </div>
            <button
              onClick={() => removeEntry(i)}
              className="p-2 text-muted-foreground hover:text-destructive transition-colors mb-0.5"
              title="Remove"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}

export function ProfileForm({ initialData, onSubmit, loading }: ProfileFormProps) {
  const [step, setStep] = useState(0);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [form, setForm] = useState<UserProfile>(() => {
    if (initialData) {
      return {
        ...emptyProfile(),
        ...initialData,
        strengthTraining: initialData.strengthTraining?.length ? initialData.strengthTraining : [emptyExercise()],
        cardioSchedule: initialData.cardioSchedule?.length ? initialData.cardioSchedule : [emptyExercise()],
        preferredUnits: initialData.preferredUnits || "METRIC",
      };
    }
    return emptyProfile();
  });

  function validateStep(s: number): boolean {
    const errs: Record<string, string> = {};
    if (s === 0) {
      if (!form.name.trim()) errs.name = "Name is required";
      if (!form.age || form.age <= 0) errs.age = "Age is required";
      if (!form.heightCm || form.heightCm <= 0) errs.heightCm = "Height is required";
      if (!form.weightKg || form.weightKg <= 0) errs.weightKg = "Weight is required";
    }
    setErrors(errs);
    return Object.keys(errs).length === 0;
  }

  function goNext() {
    if (validateStep(step)) {
      setStep((s) => s + 1);
    }
  }

  function handleSave() {
    if (validateStep(0)) {
      onSubmit(form);
    } else {
      setStep(0);
    }
  }

  const isImperial = form.preferredUnits === "IMPERIAL";

  const [imperialHeight, setImperialHeight] = useState(() => {
    if (isImperial && form.heightCm > 0) return cmToFtIn(form.heightCm);
    return { feet: 0, inches: 0 };
  });
  const [imperialWeight, setImperialWeight] = useState(() => {
    if (isImperial && form.weightKg > 0) return Math.round(kgToLbs(form.weightKg));
    return 0;
  });

  useEffect(() => {
    if (isImperial && form.heightCm > 0) {
      setImperialHeight(cmToFtIn(form.heightCm));
    }
    if (isImperial && form.weightKg > 0) {
      setImperialWeight(Math.round(kgToLbs(form.weightKg)));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [form.preferredUnits]);

  function update<K extends keyof UserProfile>(field: K, value: UserProfile[K]) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  function toggleUnits(units: "METRIC" | "IMPERIAL") {
    update("preferredUnits", units);
  }

  function handleImperialHeightChange(feet: number, inches: number) {
    setImperialHeight({ feet, inches });
    const cm = ftInToCm(feet, inches);
    update("heightCm", Math.round(cm * 10) / 10);
  }

  function handleImperialWeightChange(lbs: number) {
    setImperialWeight(lbs);
    const kg = lbsToKg(lbs);
    update("weightKg", Math.round(kg * 10) / 10);
  }

  type ListField = "dietaryRestrictions" | "goals" | "availableFoods";

  function addToList(field: ListField, value: string) {
    setForm((prev) => ({
      ...prev,
      [field]: [...(prev[field] || []), value],
    }));
  }

  function removeFromList(field: ListField, index: number) {
    setForm((prev) => ({
      ...prev,
      [field]: (prev[field] as string[]).filter((_: string, i: number) => i !== index),
    }));
  }

  return (
    <div className="animate-fade-in">
      <div className="flex items-center justify-center gap-1 sm:gap-2 mb-8">
        {STEPS.map((label, i) => (
          <button key={label} onClick={() => { if (i <= step || validateStep(step)) setStep(i); }} className="flex items-center gap-1 sm:gap-2 shrink-0">
            <div className={`flex h-8 w-8 items-center justify-center rounded-full text-xs font-bold transition-colors ${
              i < step ? "bg-primary text-primary-foreground"
                : i === step ? "bg-primary text-primary-foreground ring-4 ring-primary/20"
                : "bg-muted text-muted-foreground"
            }`}>
              {i < step ? <Check className="h-4 w-4" /> : i + 1}
            </div>
            <span className={`hidden lg:inline text-xs ${i === step ? "font-medium text-foreground" : "text-muted-foreground"}`}>
              {label}
            </span>
            {i < STEPS.length - 1 && <div className={`hidden sm:block h-px w-4 lg:w-8 ${i < step ? "bg-primary" : "bg-border"}`} />}
          </button>
        ))}
      </div>

      <Card className="max-w-2xl mx-auto">
        {step === 0 && (
          <div className="space-y-4">
            <h2 className="text-xl font-semibold mb-4">Personal Information</h2>

            <div className="flex items-center gap-2 mb-2">
              <span className="text-sm text-muted-foreground mr-2">Unit System:</span>
              <button
                onClick={() => toggleUnits("METRIC")}
                className={`px-3 py-1.5 rounded-l-lg text-sm font-medium border transition-colors ${
                  !isImperial
                    ? "bg-primary text-primary-foreground border-primary"
                    : "bg-card text-muted-foreground border-border hover:bg-secondary"
                }`}
              >
                Metric (cm / kg)
              </button>
              <button
                onClick={() => toggleUnits("IMPERIAL")}
                className={`px-3 py-1.5 rounded-r-lg text-sm font-medium border border-l-0 transition-colors ${
                  isImperial
                    ? "bg-primary text-primary-foreground border-primary"
                    : "bg-card text-muted-foreground border-border hover:bg-secondary"
                }`}
              >
                Imperial (ft / lbs)
              </button>
            </div>

            <Input label="Full Name" value={form.name} onChange={(e) => { update("name", e.target.value); setErrors((prev) => ({ ...prev, name: "" })); }} placeholder="John Doe" error={errors.name} />
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input label="Age" type="number" value={form.age || ""} onChange={(e) => { update("age", parseInt(e.target.value) || 0); setErrors((prev) => ({ ...prev, age: "" })); }} placeholder="25" error={errors.age} />
              <Select label="Gender" value={form.gender} onChange={(e) => update("gender", e.target.value)} options={GENDER_OPTIONS} />
            </div>
            <Select label="Race / Ethnicity" value={form.race} onChange={(e) => update("race", e.target.value)} options={RACE_OPTIONS} />

            {isImperial ? (
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <Input
                  label="Height (ft)"
                  type="number"
                  value={imperialHeight.feet || ""}
                  onChange={(e) => { handleImperialHeightChange(parseInt(e.target.value) || 0, imperialHeight.inches); setErrors((prev) => ({ ...prev, heightCm: "" })); }}
                  placeholder="5"
                  error={errors.heightCm}
                />
                <Input
                  label="Height (in)"
                  type="number"
                  value={imperialHeight.inches || ""}
                  onChange={(e) => { handleImperialHeightChange(imperialHeight.feet, parseInt(e.target.value) || 0); setErrors((prev) => ({ ...prev, heightCm: "" })); }}
                  placeholder="10"
                />
                <Input
                  label="Weight (lbs)"
                  type="number"
                  value={imperialWeight || ""}
                  onChange={(e) => { handleImperialWeightChange(parseInt(e.target.value) || 0); setErrors((prev) => ({ ...prev, weightKg: "" })); }}
                  placeholder="165"
                  error={errors.weightKg}
                />
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <Input label="Height (cm)" type="number" value={form.heightCm || ""} onChange={(e) => { update("heightCm", parseFloat(e.target.value) || 0); setErrors((prev) => ({ ...prev, heightCm: "" })); }} placeholder="175" error={errors.heightCm} />
                <Input label="Weight (kg)" type="number" value={form.weightKg || ""} onChange={(e) => { update("weightKg", parseFloat(e.target.value) || 0); setErrors((prev) => ({ ...prev, weightKg: "" })); }} placeholder="75" error={errors.weightKg} />
              </div>
            )}
          </div>
        )}

        {step === 1 && (
          <div className="space-y-6">
            <h2 className="text-xl font-semibold mb-4">Exercise Schedule</h2>
            <ExerciseEntryList
              label="Strength Training"
              entries={form.strengthTraining}
              onChange={(entries) => update("strengthTraining", entries)}
            />
            <ExerciseEntryList
              label="Cardio"
              entries={form.cardioSchedule}
              onChange={(entries) => update("cardioSchedule", entries)}
            />
          </div>
        )}

        {step === 2 && (
          <div className="space-y-4">
            <h2 className="text-xl font-semibold mb-4">Medical & Diet</h2>
            <Textarea
              label="Medical Information"
              value={form.medicalInfo}
              onChange={(e) => update("medicalInfo", e.target.value)}
              placeholder="List allergies, conditions, medications, supplements, and any relevant medical history..."
              rows={4}
            />
            <TagInput
              label="Dietary Restrictions"
              items={form.dietaryRestrictions}
              onAdd={(v) => addToList("dietaryRestrictions", v)}
              onRemove={(i) => removeFromList("dietaryRestrictions", i)}
              placeholder="e.g., Vegetarian, Halal, Gluten-free"
              suggestions={["Vegetarian", "Vegan", "Halal", "Kosher", "Gluten-free", "Dairy-free", "Low FODMAP", "Keto", "Paleo"]}
            />
          </div>
        )}

        {step === 3 && (
          <div className="space-y-4">
            <h2 className="text-xl font-semibold mb-4">Goals & Preferences</h2>
            <TagInput
              label="Goals"
              items={form.goals}
              onAdd={(v) => addToList("goals", v)}
              onRemove={(i) => removeFromList("goals", i)}
              placeholder="e.g., Lose weight, Build muscle, Improve energy"
              suggestions={["Lose weight", "Build muscle", "Improve energy", "Maintain weight", "Improve sleep", "Reduce body fat", "Increase endurance"]}
            />
            <TagInput
              label="Foods & Ingredients You Have"
              items={form.availableFoods}
              onAdd={(v) => addToList("availableFoods", v)}
              onRemove={(i) => removeFromList("availableFoods", i)}
              placeholder="e.g., Chicken breast, Rice, Broccoli"
            />
          </div>
        )}

        <div className="flex items-center justify-between mt-8 pt-6 border-t border-border">
          <Button variant="ghost" onClick={() => setStep((s) => s - 1)} disabled={step === 0}>
            <ChevronLeft className="h-4 w-4" /> Back
          </Button>
          {step < STEPS.length - 1 ? (
            <Button onClick={goNext}>
              Next <ChevronRight className="h-4 w-4" />
            </Button>
          ) : (
            <Button onClick={handleSave} loading={loading}>
              <Check className="h-4 w-4" /> Save Profile
            </Button>
          )}
        </div>
      </Card>
    </div>
  );
}
