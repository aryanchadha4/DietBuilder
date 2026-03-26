import { HTMLAttributes } from "react";

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  hover?: boolean;
}

export function Card({ hover, className = "", children, ...props }: CardProps) {
  return (
    <div
      className={`rounded-2xl border border-border bg-card p-6 shadow-sm
        ${hover ? "transition-shadow hover:shadow-md" : ""}
        ${className}`}
      {...props}
    >
      {children}
    </div>
  );
}

export function CardHeader({ className = "", children, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={`mb-4 ${className}`} {...props}>
      {children}
    </div>
  );
}

export function CardTitle({ className = "", children, ...props }: HTMLAttributes<HTMLHeadingElement>) {
  return (
    <h3 className={`text-lg font-semibold text-card-foreground ${className}`} {...props}>
      {children}
    </h3>
  );
}
