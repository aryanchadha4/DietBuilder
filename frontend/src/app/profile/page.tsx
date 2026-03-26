"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ProfileForm } from "@/components/ProfileForm";
import { FoodPreferencesPanel } from "@/components/FoodPreferencesPanel";
import { api, UserProfile, DietPlan } from "@/lib/api";
import { formatHeight, formatWeight } from "@/lib/units";
import { Badge } from "@/components/ui/Badge";
import toast from "react-hot-toast";
import { useRouter } from "next/navigation";
import { ListSkeleton } from "@/components/ui/Skeleton";
import { ChevronDown, ChevronUp, Flame } from "lucide-react";

export default function ProfilePage() {
  const router = useRouter();
  const [profiles, setProfiles] = useState<UserProfile[]>([]);
  const [selectedProfile, setSelectedProfile] = useState<UserProfile | undefined>();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [mode, setMode] = useState<"list" | "form">("list");
  const [plansByProfile, setPlansByProfile] = useState<Record<string, DietPlan[]>>({});
  const [expandedProfiles, setExpandedProfiles] = useState<Set<string>>(new Set());

  useEffect(() => {
    api.profiles
      .list()
      .then((p) => {
        setProfiles(p);
        if (p.length === 0) {
          setMode("form");
        }
        return Promise.all(
          p
            .filter((prof) => prof.id)
            .map((prof) =>
              api.dietPlans
                .listByProfile(prof.id!)
                .then((plans) => ({ profileId: prof.id!, plans }))
                .catch(() => ({ profileId: prof.id!, plans: [] as DietPlan[] }))
            )
        );
      })
      .then((results) => {
        const map: Record<string, DietPlan[]> = {};
        for (const { profileId, plans } of results) {
          map[profileId] = plans;
        }
        setPlansByProfile(map);
      })
      .catch(() => toast.error("Failed to load profiles"))
      .finally(() => setLoading(false));
  }, []);

  function toggleProfilePlans(profileId: string) {
    setExpandedProfiles((prev) => {
      const next = new Set(prev);
      if (next.has(profileId)) {
        next.delete(profileId);
      } else {
        next.add(profileId);
      }
      return next;
    });
  }

  async function handleSubmit(profile: UserProfile) {
    setSaving(true);
    try {
      if (selectedProfile?.id) {
        const updated = await api.profiles.update(selectedProfile.id, profile);
        setProfiles((prev) =>
          prev.map((p) => (p.id === updated.id ? updated : p))
        );
        toast.success("Profile updated!");
      } else {
        const created = await api.profiles.create(profile);
        setProfiles((prev) => [created, ...prev]);
        toast.success("Profile created!");
      }
      setMode("list");
      setSelectedProfile(undefined);
    } catch {
      toast.error("Failed to save profile");
    } finally {
      setSaving(false);
    }
  }

  if (mode === "form") {
    return (
      <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6">
        <ProfileForm
          initialData={selectedProfile}
          onSubmit={handleSubmit}
          loading={saving}
        />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold">Health Profiles</h1>
          <p className="text-sm text-muted-foreground mt-1">
            Manage your health profiles for personalized diet recommendations
          </p>
        </div>
        <button
          onClick={() => {
            setSelectedProfile(undefined);
            setMode("form");
          }}
          className="inline-flex items-center gap-2 rounded-xl bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground hover:bg-primary-dark transition-colors"
        >
          + New Profile
        </button>
      </div>

      <div className="mb-6">
        <FoodPreferencesPanel />
      </div>

      {loading ? (
        <ListSkeleton count={3} />
      ) : profiles.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 text-center animate-fade-in">
          <div className="mb-4 rounded-2xl bg-primary/10 p-4">
            <svg className="h-10 w-10 text-primary" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
            </svg>
          </div>
          <h3 className="text-lg font-semibold mb-1">No profiles yet</h3>
          <p className="text-sm text-muted-foreground max-w-sm mb-6">
            Create your first health profile to start getting personalized diet recommendations.
          </p>
          <button
            onClick={() => setMode("form")}
            className="rounded-xl bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground hover:bg-primary-dark transition-colors"
          >
            Create Your First Profile
          </button>
        </div>
      ) : (
        <div className="space-y-3">
          {profiles.map((profile) => {
            const plans = plansByProfile[profile.id!] || [];
            const isExpanded = expandedProfiles.has(profile.id!);
            return (
              <div
                key={profile.id}
                className="rounded-2xl border border-border bg-card hover:shadow-md transition-shadow animate-fade-in"
              >
                <div className="p-5 flex items-center justify-between">
                  <div>
                    <h3 className="font-semibold text-foreground">{profile.name}</h3>
                    <p className="text-sm text-muted-foreground mt-0.5">
                      {profile.age}y &middot; {profile.gender} &middot; {formatHeight(profile.heightCm, profile.preferredUnits)} &middot; {formatWeight(profile.weightKg, profile.preferredUnits)}
                    </p>
                    {profile.goals && profile.goals.length > 0 && (
                      <div className="flex flex-wrap gap-1.5 mt-2">
                        {profile.goals.map((g, i) => (
                          <span key={i} className="rounded-full bg-accent/20 text-accent-foreground px-2.5 py-0.5 text-xs font-medium">
                            {g}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                  <div className="flex items-center gap-2 shrink-0 ml-4">
                    <button
                      onClick={() => {
                        setSelectedProfile(profile);
                        setMode("form");
                      }}
                      className="rounded-lg px-3 py-1.5 text-sm font-medium text-muted-foreground hover:bg-secondary transition-colors"
                    >
                      Edit
                    </button>
                    <button
                      onClick={() => router.push(`/recommend?profileId=${profile.id}`)}
                      className="rounded-lg bg-primary/10 text-primary px-3 py-1.5 text-sm font-medium hover:bg-primary/20 transition-colors"
                    >
                      Get Diet Plan
                    </button>
                  </div>
                </div>

                {/* Plans section */}
                {plans.length > 0 && (
                  <div className="border-t border-border">
                    <button
                      onClick={() => toggleProfilePlans(profile.id!)}
                      className="w-full flex items-center justify-between px-5 py-3 text-sm text-muted-foreground hover:text-foreground hover:bg-secondary/30 transition-colors"
                    >
                      <span className="flex items-center gap-2">
                        <Flame className="h-3.5 w-3.5 text-orange-500" />
                        {plans.length} diet plan{plans.length !== 1 ? "s" : ""}
                      </span>
                      {isExpanded ? (
                        <ChevronUp className="h-4 w-4" />
                      ) : (
                        <ChevronDown className="h-4 w-4" />
                      )}
                    </button>
                    {isExpanded && (
                      <div className="px-5 pb-4 space-y-2">
                        {plans.map((plan) => (
                          <Link
                            key={plan.id}
                            href={`/plans?profileId=${profile.id}`}
                            className="flex items-center justify-between rounded-xl bg-secondary/50 p-3 hover:bg-secondary transition-colors"
                          >
                            <div>
                              <p className="text-sm font-medium">
                                {plan.dailyCalories} kcal/day
                              </p>
                              <p className="text-xs text-muted-foreground">
                                {plan.meals?.length || 0} meals &middot;{" "}
                                {plan.createdAt
                                  ? new Date(plan.createdAt).toLocaleDateString()
                                  : "Unknown date"}
                              </p>
                            </div>
                            <div className="flex items-center gap-2">
                              {plan.macroBreakdown && (
                                <div className="hidden sm:flex gap-1.5">
                                  <Badge variant="success">
                                    P {Math.round(plan.macroBreakdown.proteinPercent)}%
                                  </Badge>
                                  <Badge variant="warning">
                                    C {Math.round(plan.macroBreakdown.carbsPercent)}%
                                  </Badge>
                                  <Badge variant="info">
                                    F {Math.round(plan.macroBreakdown.fatPercent)}%
                                  </Badge>
                                </div>
                              )}
                              <span className="text-xs text-primary font-medium">View</span>
                            </div>
                          </Link>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
