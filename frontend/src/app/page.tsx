"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { api, UserProfile, Task, DietPlan } from "@/lib/api";
import { Card, CardHeader, CardTitle } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { CardSkeleton } from "@/components/ui/Skeleton";
import {
  User,
  Sparkles,
  ListTodo,
  ArrowRight,
  Flame,
  Target,
  TrendingUp,
  Leaf,
} from "lucide-react";

export default function DashboardPage() {
  const [profiles, setProfiles] = useState<UserProfile[]>([]);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [plans, setPlans] = useState<DietPlan[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      api.profiles.list().catch(() => []),
      api.tasks.list().catch(() => []),
      api.dietPlans.listAll().catch(() => []),
    ])
      .then(([p, t, d]) => {
        setProfiles(p);
        setTasks(t);
        setPlans(d);
      })
      .finally(() => setLoading(false));
  }, []);

  const pendingTasks = tasks.filter((t) => t.status === "PENDING");
  const activeTasks = tasks.filter((t) => t.status === "IN_PROGRESS");
  const completedTasks = tasks.filter((t) => t.status === "COMPLETED");

  if (loading) {
    return (
      <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6 space-y-6">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <CardSkeleton key={i} />
          ))}
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <CardSkeleton />
          <CardSkeleton />
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
      {/* Hero */}
      <div className="mb-8 animate-fade-in">
        <div className="flex items-center gap-3 mb-2">
          <div className="rounded-xl bg-primary/10 p-2">
            <Leaf className="h-6 w-6 text-primary" />
          </div>
          <h1 className="text-3xl font-bold">Welcome to DietBuilder</h1>
        </div>
        <p className="text-muted-foreground">
          Your AI-powered nutrition companion for science-backed dietary planning.
        </p>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        {[
          {
            label: "Profiles",
            value: profiles.length,
            icon: User,
            color: "text-blue-500",
            bg: "bg-blue-500/10",
          },
          {
            label: "Diet Plans",
            value: plans.length,
            icon: Flame,
            color: "text-orange-500",
            bg: "bg-orange-500/10",
          },
          {
            label: "Active Tasks",
            value: activeTasks.length + pendingTasks.length,
            icon: Target,
            color: "text-amber-500",
            bg: "bg-amber-500/10",
          },
          {
            label: "Completed",
            value: completedTasks.length,
            icon: TrendingUp,
            color: "text-green-500",
            bg: "bg-green-500/10",
          },
        ].map(({ label, value, icon: Icon, color, bg }) => (
          <Card key={label} hover className="animate-fade-in">
            <div className="flex items-center gap-3">
              <div className={`rounded-lg p-2 ${bg}`}>
                <Icon className={`h-5 w-5 ${color}`} />
              </div>
              <div>
                <p className="text-2xl font-bold">{value}</p>
                <p className="text-xs text-muted-foreground">{label}</p>
              </div>
            </div>
          </Card>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Recent diet plans */}
        <Card className="animate-fade-in">
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle className="flex items-center gap-2">
              <Sparkles className="h-4 w-4 text-primary" />
              Recent Diet Plans
            </CardTitle>
            <Link
              href="/plans"
              className="text-xs text-primary hover:underline flex items-center gap-1"
            >
              View all <ArrowRight className="h-3 w-3" />
            </Link>
          </CardHeader>
          {plans.length === 0 ? (
            <div className="text-center py-8">
              <Sparkles className="h-8 w-8 text-muted-foreground/40 mx-auto mb-2" />
              <p className="text-sm text-muted-foreground">No diet plans yet</p>
              <Link
                href="/recommend"
                className="text-sm text-primary hover:underline mt-1 inline-block"
              >
                Generate your first plan
              </Link>
            </div>
          ) : (
            <div className="space-y-3">
              {plans.slice(0, 3).map((plan) => (
                <div
                  key={plan.id}
                  className="flex items-center justify-between rounded-xl bg-secondary/50 p-3"
                >
                  <div>
                    <p className="text-sm font-medium">
                      {plan.dailyCalories} kcal/day
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {plan.meals?.length || 0} meals &middot;{" "}
                      {plan.createdAt
                        ? new Date(plan.createdAt).toLocaleDateString()
                        : ""}
                    </p>
                  </div>
                  {plan.macroBreakdown && (
                    <div className="flex gap-2 text-xs">
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
                </div>
              ))}
            </div>
          )}
        </Card>

        {/* Pending tasks */}
        <Card className="animate-fade-in">
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle className="flex items-center gap-2">
              <ListTodo className="h-4 w-4 text-primary" />
              Pending Tasks
            </CardTitle>
            <Link
              href="/tasks"
              className="text-xs text-primary hover:underline flex items-center gap-1"
            >
              View all <ArrowRight className="h-3 w-3" />
            </Link>
          </CardHeader>
          {pendingTasks.length === 0 && activeTasks.length === 0 ? (
            <div className="text-center py-8">
              <ListTodo className="h-8 w-8 text-muted-foreground/40 mx-auto mb-2" />
              <p className="text-sm text-muted-foreground">All caught up!</p>
              <Link
                href="/tasks"
                className="text-sm text-primary hover:underline mt-1 inline-block"
              >
                Create a task
              </Link>
            </div>
          ) : (
            <div className="space-y-2">
              {[...activeTasks, ...pendingTasks].slice(0, 5).map((task) => (
                <div
                  key={task.id}
                  className="flex items-center gap-3 rounded-xl bg-secondary/50 p-3"
                >
                  <div
                    className={`h-2 w-2 rounded-full ${
                      task.status === "IN_PROGRESS"
                        ? "bg-blue-500"
                        : "bg-amber-500"
                    }`}
                  />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium truncate">{task.title}</p>
                  </div>
                  <Badge
                    variant={task.status === "IN_PROGRESS" ? "info" : "warning"}
                  >
                    {task.status === "IN_PROGRESS" ? "Active" : "Pending"}
                  </Badge>
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>

      {/* Quick actions */}
      {profiles.length === 0 && (
        <Card className="mt-6 bg-gradient-to-r from-primary/5 to-accent/5 border-primary/20 animate-fade-in">
          <div className="text-center py-4">
            <h3 className="text-lg font-semibold mb-1">Get Started</h3>
            <p className="text-sm text-muted-foreground mb-4">
              Create your health profile to receive personalized AI-powered diet recommendations.
            </p>
            <Link
              href="/profile"
              className="inline-flex items-center gap-2 rounded-xl bg-primary px-6 py-2.5 text-sm font-medium text-primary-foreground hover:bg-primary-dark transition-colors"
            >
              Create Your Profile
              <ArrowRight className="h-4 w-4" />
            </Link>
          </div>
        </Card>
      )}
    </div>
  );
}
