"use client";

import { useState } from "react";
import { Check, Copy, ExternalLink, FileCode2 } from "lucide-react";
import type { Citation } from "@/lib/api";
import { LanguageBadge } from "@/components/dashboard/language-badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export function CitationCard({
  citation,
  githubUrl,
  defaultBranch = "main",
}: {
  citation: Citation;
  githubUrl?: string | null;
  defaultBranch?: string;
}) {
  const [copied, setCopied] = useState(false);

  const fileName = citation.filePath.split("/").pop() ?? citation.filePath;
  const dirPath = citation.filePath.includes("/")
    ? citation.filePath.substring(0, citation.filePath.lastIndexOf("/"))
    : "";

  const lineRangeText =
    citation.startLine != null && citation.endLine != null
      ? citation.startLine === citation.endLine
        ? `L${citation.startLine}`
        : `L${citation.startLine}-${citation.endLine}`
      : null;

  const fullGithubUrl =
    githubUrl && lineRangeText
      ? `${githubUrl}/blob/${defaultBranch}/${citation.filePath}#${
          citation.startLine === citation.endLine
            ? `L${citation.startLine}`
            : `L${citation.startLine}-L${citation.endLine}`
        }`
      : githubUrl
      ? `${githubUrl}/blob/${defaultBranch}/${citation.filePath}`
      : null;

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(citation.filePath);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // ignore
    }
  };

  return (
    <div className="group relative flex flex-col gap-1 rounded-xl border border-border/70 bg-card/60 p-2.5 text-xs shadow-xs backdrop-blur-sm transition-all hover:border-primary/40 hover:bg-card/90">
      <div className="flex items-center justify-between gap-2">
        <div className="flex min-w-0 items-center gap-1.5 font-medium text-foreground">
          <FileCode2 className="size-3.5 shrink-0 text-primary" />
          <span className="truncate font-mono font-semibold" title={citation.filePath}>
            {fileName}
          </span>
          {lineRangeText && (
            <span className="shrink-0 rounded-md bg-muted px-1.5 py-0.5 font-mono text-[10px] text-muted-foreground">
              {lineRangeText}
            </span>
          )}
        </div>

        <div className="flex shrink-0 items-center gap-1">
          <Button
            variant="ghost"
            size="icon-xs"
            onClick={handleCopy}
            className="size-6 text-muted-foreground hover:text-foreground"
            title="Copy path"
          >
            {copied ? (
              <Check className="size-3 text-emerald-500" />
            ) : (
              <Copy className="size-3" />
            )}
          </Button>

          {fullGithubUrl && (
            <a
              href={fullGithubUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex size-6 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
              title="View on GitHub"
            >
              <ExternalLink className="size-3" />
            </a>
          )}
        </div>
      </div>

      {dirPath && (
        <p className="truncate font-mono text-[11px] text-muted-foreground/80" title={citation.filePath}>
          {dirPath}/
        </p>
      )}

      {citation.language && (
        <div className="mt-0.5">
          <LanguageBadge
            language={citation.language}
            iconSize="sm"
            className="scale-90 origin-left"
          />
        </div>
      )}
    </div>
  );
}

export function CitationList({
  citations,
  githubUrl,
  defaultBranch,
}: {
  citations?: Citation[];
  githubUrl?: string | null;
  defaultBranch?: string;
}) {
  if (!citations || citations.length === 0) return null;

  return (
    <div className="mt-3 flex flex-col gap-1.5 border-t border-border/50 pt-2.5">
      <div className="flex items-center gap-1.5 text-[11px] font-medium text-muted-foreground">
        <span>Referenced Sources ({citations.length}):</span>
      </div>
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3">
        {citations.map((c, i) => (
          <CitationCard
            key={`${c.filePath}-${c.startLine}-${i}`}
            citation={c}
            githubUrl={githubUrl}
            defaultBranch={defaultBranch}
          />
        ))}
      </div>
    </div>
  );
}
