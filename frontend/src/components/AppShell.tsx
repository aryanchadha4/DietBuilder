"use client";

import { usePathname } from "next/navigation";
import { Navbar } from "./Navbar";
import { AuthGuard } from "./AuthGuard";

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const isLoginPage = pathname === "/login";

  if (isLoginPage) {
    return <main className="flex-1">{children}</main>;
  }

  return (
    <AuthGuard>
      <Navbar />
      <main className="flex-1">{children}</main>
    </AuthGuard>
  );
}
