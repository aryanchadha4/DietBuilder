"use client";

import { Task } from "@/lib/api";
import { Badge } from "./ui/Badge";
import { Trash2, GripVertical } from "lucide-react";

const STATUS_BADGE: Record<Task["status"], { variant: "warning" | "info" | "success"; label: string }> = {
  PENDING: { variant: "warning", label: "Pending" },
  IN_PROGRESS: { variant: "info", label: "In Progress" },
  COMPLETED: { variant: "success", label: "Completed" },
};

const PRIORITY_COLOR: Record<Task["priority"], string> = {
  LOW: "bg-green-400",
  MEDIUM: "bg-amber-400",
  HIGH: "bg-red-400",
};

interface TaskListProps {
  tasks: Task[];
  onStatusChange: (id: string, status: Task["status"]) => void;
  onDelete: (id: string) => void;
}

export function TaskList({ tasks, onStatusChange, onDelete }: TaskListProps) {
  const nextStatus: Record<Task["status"], Task["status"]> = {
    PENDING: "IN_PROGRESS",
    IN_PROGRESS: "COMPLETED",
    COMPLETED: "PENDING",
  };

  return (
    <div className="space-y-2">
      {tasks.map((task) => {
        const badge = STATUS_BADGE[task.status];
        return (
          <div
            key={task.id}
            className="group rounded-2xl border border-border bg-card p-4 flex items-center gap-3 hover:shadow-md transition-all animate-fade-in"
          >
            <GripVertical className="h-4 w-4 text-muted-foreground/40 shrink-0" />

            <div className={`h-2.5 w-2.5 rounded-full shrink-0 ${PRIORITY_COLOR[task.priority]}`} />

            <button
              onClick={() => onStatusChange(task.id!, nextStatus[task.status])}
              className={`h-5 w-5 shrink-0 rounded-md border-2 flex items-center justify-center transition-colors ${
                task.status === "COMPLETED"
                  ? "bg-primary border-primary text-primary-foreground"
                  : "border-border hover:border-primary"
              }`}
            >
              {task.status === "COMPLETED" && (
                <svg className="h-3 w-3" viewBox="0 0 12 12" fill="none">
                  <path d="M2 6l3 3 5-5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              )}
            </button>

            <div className="flex-1 min-w-0">
              <p
                className={`text-sm font-medium truncate ${
                  task.status === "COMPLETED"
                    ? "line-through text-muted-foreground"
                    : "text-foreground"
                }`}
              >
                {task.title}
              </p>
              {task.description && (
                <p className="text-xs text-muted-foreground truncate mt-0.5">
                  {task.description}
                </p>
              )}
            </div>

            <Badge variant={badge.variant}>{badge.label}</Badge>

            {task.dueDate && (
              <span className="text-xs text-muted-foreground hidden sm:inline">
                {new Date(task.dueDate).toLocaleDateString()}
              </span>
            )}

            <button
              onClick={() => onDelete(task.id!)}
              className="opacity-0 group-hover:opacity-100 p-1.5 rounded-lg hover:bg-destructive/10 text-muted-foreground hover:text-destructive transition-all"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          </div>
        );
      })}
    </div>
  );
}
