"use client";

import { useEffect, useMemo, useState } from "react";
import { api, ExpertSource } from "@/lib/api";
import { Card, CardHeader, CardTitle } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Badge } from "@/components/ui/Badge";
import toast from "react-hot-toast";
import { Database, RefreshCw } from "lucide-react";

export default function AdminSourcesPage() {
  const [sources, setSources] = useState<ExpertSource[]>([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(false);
  const [newSource, setNewSource] = useState<Partial<ExpertSource>>({
    title: "",
    sourceType: "PEER_REVIEWED",
    summary: "",
    credibilityScore: 0.8,
    active: true,
  });
  const [stats, setStats] = useState<Record<string, unknown> | null>(null);

  async function load() {
    setLoading(true);
    try {
      const [allSources, sourceStats] = await Promise.all([
        api.admin.sources(),
        api.admin.sourceStats(),
      ]);
      setSources(allSources);
      setStats(sourceStats);
    } catch {
      toast.error("Failed to load expert sources");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return sources;
    return sources.filter(
      (s) =>
        s.title.toLowerCase().includes(q) ||
        (s.authors || "").toLowerCase().includes(q) ||
        (s.sourceType || "").toLowerCase().includes(q)
    );
  }, [sources, query]);

  async function createSource() {
    if (!newSource.title?.trim()) {
      toast.error("Title is required");
      return;
    }
    try {
      await api.admin.addSource(newSource);
      toast.success("Source added");
      setNewSource({
        title: "",
        sourceType: "PEER_REVIEWED",
        summary: "",
        credibilityScore: 0.8,
        active: true,
      });
      await load();
    } catch {
      toast.error("Failed to add source");
    }
  }

  async function toggleActive(source: ExpertSource) {
    try {
      await api.admin.updateSource(source.id!, { ...source, active: !source.active });
      toast.success(source.active ? "Source deactivated" : "Source activated");
      await load();
    } catch {
      toast.error("Failed to update source");
    }
  }

  async function reindexSource(sourceId?: string) {
    if (!sourceId) return;
    try {
      await api.admin.reindexSource(sourceId);
      toast.success("Source reindexed");
      await load();
    } catch {
      toast.error("Failed to reindex source");
    }
  }

  return (
    <div className="mx-auto max-w-6xl px-4 sm:px-6 py-8 space-y-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold flex items-center gap-3">
            <Database className="h-8 w-8 text-primary" />
            Expert Sources
          </h1>
          <p className="text-muted-foreground mt-1">
            Manage RAG knowledge sources and indexing state.
          </p>
        </div>
        <Button onClick={load} loading={loading}>
          <RefreshCw className="h-4 w-4 mr-2" />
          Refresh
        </Button>
      </div>

      {stats && (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Card className="text-center">
            <p className="text-2xl font-bold">{String(stats.totalSources || 0)}</p>
            <p className="text-xs text-muted-foreground">Total Sources</p>
          </Card>
          <Card className="text-center">
            <p className="text-2xl font-bold">{String(stats.activeSources || 0)}</p>
            <p className="text-xs text-muted-foreground">Active Sources</p>
          </Card>
          <Card className="text-center">
            <p className="text-2xl font-bold">{Math.round(Number(stats.averagePublicationAgeYears || 0))}</p>
            <p className="text-xs text-muted-foreground">Avg Age (Years)</p>
          </Card>
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Add Source</CardTitle>
        </CardHeader>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <Input
            placeholder="Title"
            value={newSource.title || ""}
            onChange={(e) => setNewSource((s) => ({ ...s, title: e.target.value }))}
          />
          <Input
            placeholder="Source type (e.g. META_ANALYSIS)"
            value={newSource.sourceType || ""}
            onChange={(e) => setNewSource((s) => ({ ...s, sourceType: e.target.value }))}
          />
          <Input
            placeholder="Authors or organization"
            value={newSource.authors || ""}
            onChange={(e) => setNewSource((s) => ({ ...s, authors: e.target.value }))}
          />
          <Input
            placeholder="Publication year"
            value={newSource.publicationDate || ""}
            onChange={(e) => setNewSource((s) => ({ ...s, publicationDate: e.target.value }))}
          />
          <Input
            placeholder="URL"
            value={newSource.url || ""}
            onChange={(e) => setNewSource((s) => ({ ...s, url: e.target.value }))}
          />
          <Input
            placeholder="DOI"
            value={newSource.doi || ""}
            onChange={(e) => setNewSource((s) => ({ ...s, doi: e.target.value }))}
          />
          <Input
            className="md:col-span-2"
            placeholder="Short summary"
            value={newSource.summary || ""}
            onChange={(e) => setNewSource((s) => ({ ...s, summary: e.target.value }))}
          />
        </div>
        <div className="mt-3">
          <Button onClick={createSource}>Add Source</Button>
        </div>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Source Library</CardTitle>
        </CardHeader>
        <div className="space-y-3">
          <Input
            placeholder="Search by title, author, or type..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          {filtered.map((source) => (
            <div
              key={source.id}
              className="rounded-xl border border-border p-3 flex items-start justify-between gap-3"
            >
              <div>
                <p className="font-medium text-sm">{source.title}</p>
                <p className="text-xs text-muted-foreground">
                  {source.authors || source.organization || "Unknown"}
                  {source.publicationDate ? ` • ${source.publicationDate}` : ""}
                </p>
                <div className="flex items-center gap-2 mt-2">
                  {source.sourceType && <Badge variant="secondary">{source.sourceType}</Badge>}
                  <Badge variant={source.active ? "success" : "outline"}>
                    {source.active ? "Active" : "Inactive"}
                  </Badge>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <Button variant="secondary" size="sm" onClick={() => reindexSource(source.id)}>
                  Reindex
                </Button>
                <Button variant="secondary" size="sm" onClick={() => toggleActive(source)}>
                  {source.active ? "Deactivate" : "Activate"}
                </Button>
              </div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}
