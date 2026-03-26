"use client";

import { useEffect, useState } from "react";
import { api, Task } from "@/lib/api";
import { TaskList } from "@/components/TaskList";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { ListSkeleton } from "@/components/ui/Skeleton";
import toast from "react-hot-toast";
import { ListTodo, Plus, Filter } from "lucide-react";

const STATUS_FILTERS = [
  { value: "ALL", label: "All Tasks" },
  { value: "PENDING", label: "Pending" },
  { value: "IN_PROGRESS", label: "In Progress" },
  { value: "COMPLETED", label: "Completed" },
];

const PRIORITY_OPTIONS = [
  { value: "LOW", label: "Low" },
  { value: "MEDIUM", label: "Medium" },
  { value: "HIGH", label: "High" },
];

export default function TasksPage() {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [filter, setFilter] = useState("ALL");
  const [newTitle, setNewTitle] = useState("");
  const [newDescription, setNewDescription] = useState("");
  const [newPriority, setNewPriority] = useState<Task["priority"]>("MEDIUM");
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    loadTasks();
  }, []);

  async function loadTasks() {
    try {
      const data = await api.tasks.list();
      setTasks(data);
    } catch {
      toast.error("Failed to load tasks");
    } finally {
      setLoading(false);
    }
  }

  async function handleCreate() {
    if (!newTitle.trim()) {
      toast.error("Title is required");
      return;
    }
    setCreating(true);
    try {
      const task = await api.tasks.create({
        title: newTitle.trim(),
        description: newDescription.trim() || undefined,
        priority: newPriority,
        status: "PENDING",
      });
      setTasks((prev) => [task, ...prev]);
      setNewTitle("");
      setNewDescription("");
      setNewPriority("MEDIUM");
      setShowForm(false);
      toast.success("Task created!");
    } catch {
      toast.error("Failed to create task");
    } finally {
      setCreating(false);
    }
  }

  async function handleStatusChange(id: string, status: Task["status"]) {
    try {
      const updated = await api.tasks.update(id, { status });
      setTasks((prev) => prev.map((t) => (t.id === id ? updated : t)));
    } catch {
      toast.error("Failed to update task");
    }
  }

  async function handleDelete(id: string) {
    try {
      await api.tasks.delete(id);
      setTasks((prev) => prev.filter((t) => t.id !== id));
      toast.success("Task deleted");
    } catch {
      toast.error("Failed to delete task");
    }
  }

  const filteredTasks =
    filter === "ALL" ? tasks : tasks.filter((t) => t.status === filter);

  const counts = {
    total: tasks.length,
    pending: tasks.filter((t) => t.status === "PENDING").length,
    inProgress: tasks.filter((t) => t.status === "IN_PROGRESS").length,
    completed: tasks.filter((t) => t.status === "COMPLETED").length,
  };

  return (
    <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold">Tasks</h1>
          <p className="text-sm text-muted-foreground mt-1">
            Track your diet-related goals and to-dos
          </p>
        </div>
        <Button onClick={() => setShowForm(!showForm)}>
          <Plus className="h-4 w-4" />
          New Task
        </Button>
      </div>

      {/* Quick stats */}
      {tasks.length > 0 && (
        <div className="grid grid-cols-4 gap-3 mb-6">
          {[
            { label: "Total", value: counts.total, color: "text-foreground" },
            { label: "Pending", value: counts.pending, color: "text-amber-500" },
            { label: "Active", value: counts.inProgress, color: "text-blue-500" },
            { label: "Done", value: counts.completed, color: "text-green-500" },
          ].map(({ label, value, color }) => (
            <div key={label} className="rounded-xl border border-border bg-card p-3 text-center">
              <p className={`text-xl font-bold ${color}`}>{value}</p>
              <p className="text-xs text-muted-foreground">{label}</p>
            </div>
          ))}
        </div>
      )}

      {/* New task form */}
      {showForm && (
        <Card className="mb-6 animate-fade-in">
          <h3 className="text-sm font-semibold mb-3">Create New Task</h3>
          <div className="space-y-3">
            <Input
              label="Title"
              value={newTitle}
              onChange={(e) => setNewTitle(e.target.value)}
              placeholder="e.g., Meal prep for the week"
              onKeyDown={(e) => {
                if (e.key === "Enter") handleCreate();
              }}
            />
            <Input
              label="Description (optional)"
              value={newDescription}
              onChange={(e) => setNewDescription(e.target.value)}
              placeholder="Additional details..."
            />
            <Select
              label="Priority"
              value={newPriority}
              onChange={(e) => setNewPriority(e.target.value as Task["priority"])}
              options={PRIORITY_OPTIONS}
            />
            <div className="flex gap-2 pt-2">
              <Button onClick={handleCreate} loading={creating}>
                Create Task
              </Button>
              <Button variant="ghost" onClick={() => setShowForm(false)}>
                Cancel
              </Button>
            </div>
          </div>
        </Card>
      )}

      {/* Filter */}
      {tasks.length > 0 && (
        <div className="flex items-center gap-2 mb-4">
          <Filter className="h-4 w-4 text-muted-foreground" />
          <div className="flex gap-1">
            {STATUS_FILTERS.map(({ value, label }) => (
              <button
                key={value}
                onClick={() => setFilter(value)}
                className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-colors ${
                  filter === value
                    ? "bg-primary/10 text-primary"
                    : "text-muted-foreground hover:bg-secondary"
                }`}
              >
                {label}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Task list */}
      {loading ? (
        <ListSkeleton count={5} />
      ) : tasks.length === 0 ? (
        <EmptyState
          icon={ListTodo}
          title="No tasks yet"
          description="Create tasks to track your diet goals, meal prep reminders, and health milestones."
          actionLabel="Create Your First Task"
          onAction={() => setShowForm(true)}
        />
      ) : filteredTasks.length === 0 ? (
        <div className="text-center py-12 text-muted-foreground text-sm">
          No tasks match this filter.
        </div>
      ) : (
        <TaskList
          tasks={filteredTasks}
          onStatusChange={handleStatusChange}
          onDelete={handleDelete}
        />
      )}
    </div>
  );
}
