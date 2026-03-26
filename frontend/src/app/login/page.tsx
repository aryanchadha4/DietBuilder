"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/AuthContext";
import { Leaf, LogIn, UserPlus, Eye, EyeOff } from "lucide-react";
import toast from "react-hot-toast";

type Tab = "login" | "register";

export default function LoginPage() {
  const [tab, setTab] = useState<Tab>("login");
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const { login, register } = useAuth();
  const router = useRouter();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      if (tab === "login") {
        await login(username, password);
        toast.success("Welcome back!");
        router.replace("/");
      } else {
        await register(username, email, password);
        toast.success("Account created! Let's set up your profile.");
        router.replace("/profile");
      }
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Something went wrong";
      toast.error(message.replace(/^API Error \d+:\s*/, "").replace(/[{}"]/g, "").trim());
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-background px-4">
      <div className="w-full max-w-md animate-fade-in">
        <div className="flex flex-col items-center mb-8">
          <div className="flex items-center gap-2 mb-2">
            <Leaf className="h-8 w-8 text-primary" />
            <span className="text-2xl font-bold">DietBuilder</span>
          </div>
          <p className="text-muted-foreground text-sm">
            AI-powered dietary planning for your goals
          </p>
        </div>

        <div className="bg-card rounded-2xl border border-border shadow-lg overflow-hidden">
          <div className="flex border-b border-border">
            <button
              onClick={() => setTab("login")}
              className={`flex-1 flex items-center justify-center gap-2 py-3.5 text-sm font-medium transition-colors ${
                tab === "login"
                  ? "bg-primary/10 text-primary border-b-2 border-primary"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              <LogIn className="h-4 w-4" />
              Sign In
            </button>
            <button
              onClick={() => setTab("register")}
              className={`flex-1 flex items-center justify-center gap-2 py-3.5 text-sm font-medium transition-colors ${
                tab === "register"
                  ? "bg-primary/10 text-primary border-b-2 border-primary"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              <UserPlus className="h-4 w-4" />
              Create Account
            </button>
          </div>

          <form onSubmit={handleSubmit} className="p-6 space-y-4">
            <div>
              <label className="block text-sm font-medium mb-1.5">Username</label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="w-full rounded-lg border border-border bg-background px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
                placeholder="Enter your username"
                required
                autoComplete="username"
              />
            </div>

            {tab === "register" && (
              <div className="animate-fade-in">
                <label className="block text-sm font-medium mb-1.5">Email</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full rounded-lg border border-border bg-background px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
                  placeholder="you@example.com"
                  required
                  autoComplete="email"
                />
              </div>
            )}

            <div>
              <label className="block text-sm font-medium mb-1.5">Password</label>
              <div className="relative">
                <input
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full rounded-lg border border-border bg-background px-3 py-2.5 pr-10 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
                  placeholder="Enter your password"
                  required
                  minLength={6}
                  autoComplete={tab === "login" ? "current-password" : "new-password"}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full rounded-lg bg-primary py-2.5 text-sm font-semibold text-primary-foreground hover:bg-primary-dark transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading
                ? "Please wait..."
                : tab === "login"
                ? "Sign In"
                : "Create Account"}
            </button>
          </form>
        </div>

        <p className="text-center text-xs text-muted-foreground mt-6">
          {tab === "login" ? (
            <>
              Don&apos;t have an account?{" "}
              <button onClick={() => setTab("register")} className="text-primary hover:underline font-medium">
                Create one
              </button>
            </>
          ) : (
            <>
              Already have an account?{" "}
              <button onClick={() => setTab("login")} className="text-primary hover:underline font-medium">
                Sign in
              </button>
            </>
          )}
        </p>
      </div>
    </div>
  );
}
