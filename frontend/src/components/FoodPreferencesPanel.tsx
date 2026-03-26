"use client";

import { useState, useEffect, useSyncExternalStore } from "react";
import { api, FoodPreference } from "@/lib/api";
import { Card, CardHeader, CardTitle } from "./ui/Card";
import { Badge } from "./ui/Badge";
import {
  ShieldBan,
  Clock,
  Trash2,
  ArrowUp,
  RotateCcw,
  AlertTriangle,
} from "lucide-react";
import toast from "react-hot-toast";

const WALL_CLOCK_TICK_MS = 60_000;

function createWallClockMsStore(intervalMs: number) {
  let wallMs = 0;
  let intervalId: ReturnType<typeof setInterval> | undefined;
  const listeners = new Set<() => void>();

  function subscribe(onStoreChange: () => void) {
    wallMs = Date.now();
    if (intervalId === undefined) {
      intervalId = setInterval(() => {
        wallMs = Date.now();
        listeners.forEach((l) => l());
      }, intervalMs);
    }
    listeners.add(onStoreChange);
    return () => {
      listeners.delete(onStoreChange);
      if (listeners.size === 0 && intervalId !== undefined) {
        clearInterval(intervalId);
        intervalId = undefined;
      }
    };
  }

  function getSnapshot() {
    return wallMs;
  }

  function getServerSnapshot() {
    return 0;
  }

  return { subscribe, getSnapshot, getServerSnapshot };
}

const wallClockMsStore = createWallClockMsStore(WALL_CLOCK_TICK_MS);

export function FoodPreferencesPanel() {
  const [prefs, setPrefs] = useState<FoodPreference[]>([]);
  const [loading, setLoading] = useState(true);

  const load = () => {
    api.foodPreferences
      .list()
      .then(setPrefs)
      .catch(() => setPrefs([]))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const permanent = prefs.filter((p) => p.type === "PERMANENT");
  const temporary = prefs.filter((p) => p.type === "TEMPORARY");

  const handleRemove = async (id: string) => {
    try {
      await api.foodPreferences.remove(id);
      setPrefs((prev) => prev.filter((p) => p.id !== id));
      toast.success("Food preference removed");
    } catch {
      toast.error("Failed to remove preference");
    }
  };

  const handleUpgrade = async (id: string) => {
    try {
      const updated = await api.foodPreferences.update(id, "PERMANENT");
      setPrefs((prev) => prev.map((p) => (p.id === id ? updated : p)));
      toast.success("Upgraded to permanent exclusion");
    } catch {
      toast.error("Failed to update preference");
    }
  };

  const handleResetTemporary = async () => {
    try {
      await api.foodPreferences.resetTemporary();
      setPrefs((prev) => prev.filter((p) => p.type === "PERMANENT"));
      toast.success("All temporary dislikes cleared");
    } catch {
      toast.error("Failed to reset temporary preferences");
    }
  };

  if (loading) {
    return (
      <Card>
        <div className="animate-pulse space-y-3">
          <div className="h-4 w-48 bg-secondary rounded" />
          <div className="h-3 w-64 bg-secondary rounded" />
          <div className="h-3 w-32 bg-secondary rounded" />
        </div>
      </Card>
    );
  }

  if (prefs.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <ShieldBan className="h-5 w-5 text-muted-foreground" />
            Food Preferences
          </CardTitle>
        </CardHeader>
        <p className="text-sm text-muted-foreground">
          No excluded foods yet. You can reject foods directly from your diet
          plan by hovering over a food item and clicking the &times; button.
        </p>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle className="flex items-center gap-2 text-base">
          <ShieldBan className="h-5 w-5 text-muted-foreground" />
          Food Preferences
          <Badge>{prefs.length} excluded</Badge>
        </CardTitle>
      </CardHeader>

      {/* Permanent exclusions */}
      {permanent.length > 0 && (
        <div className="mb-4">
          <h4 className="flex items-center gap-1.5 text-sm font-medium text-red-600 dark:text-red-400 mb-2">
            <ShieldBan className="h-3.5 w-3.5" />
            Permanent Exclusions
          </h4>
          <div className="space-y-1.5">
            {permanent.map((pref) => (
              <PreferenceRow
                key={pref.id}
                pref={pref}
                onRemove={() => handleRemove(pref.id!)}
              />
            ))}
          </div>
        </div>
      )}

      {/* Temporary exclusions */}
      {temporary.length > 0 && (
        <div className="mb-4">
          <div className="flex items-center justify-between mb-2">
            <h4 className="flex items-center gap-1.5 text-sm font-medium text-amber-600 dark:text-amber-400">
              <Clock className="h-3.5 w-3.5" />
              Temporary Exclusions
            </h4>
            <button
              onClick={handleResetTemporary}
              className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition-colors"
            >
              <RotateCcw className="h-3 w-3" />
              Reset all
            </button>
          </div>
          <div className="space-y-1.5">
            {temporary.map((pref) => (
              <PreferenceRow
                key={pref.id}
                pref={pref}
                onRemove={() => handleRemove(pref.id!)}
                onUpgrade={() => handleUpgrade(pref.id!)}
              />
            ))}
          </div>
        </div>
      )}
    </Card>
  );
}

function PreferenceRow({
  pref,
  onRemove,
  onUpgrade,
}: {
  pref: FoodPreference;
  onRemove: () => void;
  onUpgrade?: () => void;
}) {
  const nowMs = useSyncExternalStore(
    wallClockMsStore.subscribe,
    wallClockMsStore.getSnapshot,
    wallClockMsStore.getServerSnapshot
  );

  const expiresText = pref.expiresAt
    ? `Expires ${new Date(pref.expiresAt).toLocaleDateString()}`
    : null;

  const isExpiringSoon =
    nowMs > 0 &&
    pref.expiresAt &&
    new Date(pref.expiresAt).getTime() - nowMs < 7 * 24 * 60 * 60 * 1000;

  return (
    <div className="flex items-center gap-2 rounded-lg border border-border px-3 py-2 text-sm group">
      <span className="flex-1 font-medium">{pref.foodName}</span>

      {pref.reason && (
        <span
          className="text-xs text-muted-foreground max-w-[120px] truncate"
          title={pref.reason}
        >
          {pref.reason}
        </span>
      )}

      {expiresText && (
        <span
          className={`text-xs ${isExpiringSoon ? "text-amber-500" : "text-muted-foreground"}`}
        >
          {isExpiringSoon && <AlertTriangle className="h-3 w-3 inline mr-0.5" />}
          {expiresText}
        </span>
      )}

      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        {onUpgrade && (
          <button
            onClick={onUpgrade}
            className="rounded p-1 hover:bg-red-100 dark:hover:bg-red-900/30 transition-colors"
            title="Make permanent"
          >
            <ArrowUp className="h-3.5 w-3.5 text-red-500" />
          </button>
        )}
        <button
          onClick={onRemove}
          className="rounded p-1 hover:bg-secondary transition-colors"
          title="Remove exclusion"
        >
          <Trash2 className="h-3.5 w-3.5 text-muted-foreground" />
        </button>
      </div>
    </div>
  );
}
