"use client";

import { SelectHTMLAttributes, forwardRef } from "react";

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  options: { value: string; label: string }[];
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(
  ({ label, error, options, className = "", id, ...props }, ref) => {
    const selectId = id || label?.toLowerCase().replace(/\s/g, "-");
    return (
      <div className="space-y-1.5">
        {label && (
          <label htmlFor={selectId} className="block text-sm font-medium text-foreground">
            {label}
          </label>
        )}
        <select
          ref={ref}
          id={selectId}
          className={`w-full rounded-xl border bg-card px-4 py-2.5 text-sm text-foreground
            transition-colors focus:border-primary focus:outline-none focus:ring-2 focus:ring-ring/20
            disabled:opacity-50
            ${error ? "border-destructive" : "border-border"}
            ${className}`}
          {...props}
        >
          {options.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
        {error && <p className="text-xs text-destructive">{error}</p>}
      </div>
    );
  }
);

Select.displayName = "Select";
