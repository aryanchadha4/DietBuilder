"use client";

import { useState, useRef, useEffect } from "react";
import { Ban, Clock, ShieldBan, X } from "lucide-react";

interface FoodRejectionPopoverProps {
  foodName: string;
  onReject: (
    foodName: string,
    type: "PERMANENT" | "TEMPORARY",
    reason?: string
  ) => void;
  onClose: () => void;
  anchorRef?: React.RefObject<HTMLElement | null>;
}

export function FoodRejectionPopover({
  foodName,
  onReject,
  onClose,
}: FoodRejectionPopoverProps) {
  const [showReason, setShowReason] = useState(false);
  const [reason, setReason] = useState("");
  const [pendingType, setPendingType] = useState<
    "PERMANENT" | "TEMPORARY" | null
  >(null);
  const popRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (popRef.current && !popRef.current.contains(e.target as Node)) {
        onClose();
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [onClose]);

  const handleSelect = (type: "PERMANENT" | "TEMPORARY") => {
    setPendingType(type);
    setShowReason(true);
  };

  const handleConfirm = () => {
    if (pendingType) {
      onReject(foodName, pendingType, reason || undefined);
    }
  };

  return (
    <div
      ref={popRef}
      className="absolute z-50 mt-1 w-72 rounded-xl border border-border bg-card p-4 shadow-lg animate-fade-in"
    >
      <div className="flex items-center justify-between mb-3">
        <h4 className="text-sm font-semibold">
          Reject &ldquo;{foodName}&rdquo;?
        </h4>
        <button
          onClick={onClose}
          className="rounded p-0.5 hover:bg-secondary transition-colors"
        >
          <X className="h-4 w-4 text-muted-foreground" />
        </button>
      </div>

      {!showReason ? (
        <div className="space-y-2">
          <button
            onClick={() => handleSelect("PERMANENT")}
            className="w-full flex items-center gap-3 rounded-lg border border-border p-3 text-left hover:border-red-300 hover:bg-red-50/50 dark:hover:bg-red-900/10 transition-colors"
          >
            <ShieldBan className="h-5 w-5 text-red-500 flex-shrink-0" />
            <div>
              <p className="text-sm font-medium">I never want this</p>
              <p className="text-xs text-muted-foreground">
                Permanently excluded (allergies, dietary restrictions)
              </p>
            </div>
          </button>
          <button
            onClick={() => handleSelect("TEMPORARY")}
            className="w-full flex items-center gap-3 rounded-lg border border-border p-3 text-left hover:border-amber-300 hover:bg-amber-50/50 dark:hover:bg-amber-900/10 transition-colors"
          >
            <Clock className="h-5 w-5 text-amber-500 flex-shrink-0" />
            <div>
              <p className="text-sm font-medium">Not right now</p>
              <p className="text-xs text-muted-foreground">
                Temporarily excluded for 30 days
              </p>
            </div>
          </button>
        </div>
      ) : (
        <div className="space-y-3">
          <div className="flex items-center gap-2 text-xs">
            {pendingType === "PERMANENT" ? (
              <Badge type="permanent" />
            ) : (
              <Badge type="temporary" />
            )}
          </div>
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Reason (optional)..."
            className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm resize-none focus:border-primary focus:outline-none focus:ring-2 focus:ring-ring/20"
            rows={2}
          />
          <div className="flex gap-2">
            <button
              onClick={() => {
                setShowReason(false);
                setPendingType(null);
                setReason("");
              }}
              className="flex-1 rounded-lg border border-border px-3 py-2 text-sm hover:bg-secondary transition-colors"
            >
              Back
            </button>
            <button
              onClick={handleConfirm}
              className="flex-1 rounded-lg bg-red-500 px-3 py-2 text-sm font-medium text-white hover:bg-red-600 transition-colors flex items-center justify-center gap-1.5"
            >
              <Ban className="h-3.5 w-3.5" />
              Exclude
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function Badge({ type }: { type: "permanent" | "temporary" }) {
  if (type === "permanent") {
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-700 dark:bg-red-900/30 dark:text-red-400">
        <ShieldBan className="h-3 w-3" />
        Permanent
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700 dark:bg-amber-900/30 dark:text-amber-400">
      <Clock className="h-3 w-3" />
      Temporary (30 days)
    </span>
  );
}
