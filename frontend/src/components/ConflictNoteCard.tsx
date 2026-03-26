"use client";

import { ConflictNote } from "@/lib/api";
import { Card, CardHeader, CardTitle } from "./ui/Card";

export function ConflictNoteCard({ note }: { note: ConflictNote }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Mixed Evidence: {note.topic}</CardTitle>
      </CardHeader>
      <p className="text-sm text-muted-foreground">{note.summary}</p>
      <div className="mt-3 text-xs text-muted-foreground space-y-1">
        <p>Supporting sources: {note.supportingSourceIds?.join(", ") || "None"}</p>
        <p>Opposing sources: {note.opposingSourceIds?.join(", ") || "None"}</p>
        {note.resolution && <p>Resolution: {note.resolution}</p>}
        {note.resolutionBasis && <p>Basis: {note.resolutionBasis}</p>}
      </div>
    </Card>
  );
}
