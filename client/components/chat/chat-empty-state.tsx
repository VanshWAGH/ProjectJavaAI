"use client";

import {
  Compass,
  FileCode2,
  GitPullRequest,
  Loader2,
  ShieldCheck,
  Terminal,
  Zap,
} from "lucide-react";
import type { Repository } from "@/lib/api";
import { DevPilotIcon } from "@/components/icons/devpilot-icon";
import { LanguageBadge } from "@/components/dashboard/language-badge";
import { Progress } from "@/components/ui/progress";

const STARTER_CARDS = [
  {
    icon: Compass,
    title: "Project Architecture",
    description: "Provide a high-level overview of the structure and modules in this repository.",
    prompt: "Can you provide a high-level architecture overview of this codebase, highlighting the main directories and their responsibilities?",
  },
  {
    icon: FileCode2,
    title: "Key Data Flow & Models",
    description: "Explain how data flows between controllers, services, and repositories.",
    prompt: "Explain how requests flow through this application from entry points to database queries.",
  },
  {
    icon: ShieldCheck,
    title: "Security & Authentication",
    description: "Inspect how authentication, sessions, and permissions are enforced.",
    prompt: "How does authentication and authorization work in this repository? What security measures are implemented?",
  },
  {
    icon: Terminal,
    title: "Setup & Running Locally",
    description: "Find setup steps, dependencies, Docker commands, or configuration keys.",
    prompt: "What are the prerequisites, environment variables, and steps needed to run this project locally?",
  },
];

export function ChatEmptyState({
  repo,
  onSelectPrompt,
}: {
  repo?: Repository;
  onSelectPrompt: (prompt: string) => void;
}) {
  const isIndexing = repo?.indexStatus === "INDEXING";
  const isPending = repo?.indexStatus === "PENDING";
  const isFailed = repo?.indexStatus === "FAILED";
  const isReady = repo?.indexStatus === "READY";

  const progressPercent =
    repo?.filesTotal && repo.filesTotal > 0
      ? Math.round(((repo.filesProcessed || 0) / repo.filesTotal) * 100)
      : 0;

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col items-center justify-center px-4 py-8 text-center sm:py-12">
      <div className="relative mb-4 flex size-16 items-center justify-center rounded-2xl bg-muted/60 shadow-md">
        <DevPilotIcon className="size-10" />
        {isIndexing && (
          <span className="absolute -top-1 -right-1 flex size-5 items-center justify-center rounded-full bg-amber-500 shadow-sm">
            <Loader2 className="size-3 animate-spin text-white" />
          </span>
        )}
      </div>

      <h2 className="font-heading text-xl font-bold tracking-tight text-foreground sm:text-2xl">
        Chat with {repo ? repo.name : "Repository"}
      </h2>

      {/* Context-aware subtitle */}
      <p className="mt-2 max-w-md text-sm text-muted-foreground">
        {isIndexing
          ? "DevPilot is indexing this codebase. You can start chatting now — answers will improve as more files are processed."
          : isPending
            ? "This repository hasn't been indexed yet. Start indexing to unlock AI-powered code chat."
            : isFailed
              ? "Indexing failed. You can retry indexing from the header, or ask questions about any already-indexed chunks."
              : "DevPilot has indexed this codebase. Ask questions, explore logic, or locate functions with line-precise citations."}
      </p>

      {/* Live indexing progress bar */}
      {isIndexing && repo && (
        <div className="mt-4 w-full max-w-sm space-y-2 rounded-xl border border-amber-500/30 bg-amber-500/5 p-4">
          <div className="flex items-center justify-between text-xs">
            <span className="flex items-center gap-1.5 font-medium text-amber-600 dark:text-amber-400">
              <Loader2 className="size-3 animate-spin" />
              Indexing in progress…
            </span>
            <span className="font-mono text-muted-foreground">
              {repo.filesProcessed || 0} / {repo.filesTotal || "?"} files
            </span>
          </div>
          <Progress value={progressPercent || 3} className="h-2" />
          <div className="flex items-center justify-between text-[11px] text-muted-foreground">
            <span>{progressPercent}% complete</span>
            <span>{repo.chunkCount || 0} chunks created</span>
          </div>
        </div>
      )}

      {/* Static stats for ready/failed repos */}
      {(isReady || (isFailed && (repo?.chunkCount ?? 0) > 0)) && repo && (
        <div className="mt-3 flex flex-wrap items-center justify-center gap-2 text-xs">
          <LanguageBadge language={repo.language} iconSize="sm" />
          <span className="rounded-md border border-border/60 bg-muted/40 px-2 py-0.5 font-mono text-[11px] text-muted-foreground">
            {repo.filesProcessed || repo.filesTotal || 0} files indexed
          </span>
          <span className="rounded-md border border-border/60 bg-muted/40 px-2 py-0.5 font-mono text-[11px] text-muted-foreground">
            {repo.chunkCount || 0} code chunks
          </span>
        </div>
      )}

      {/* Pending state stats */}
      {isPending && repo && (
        <div className="mt-3 flex flex-wrap items-center justify-center gap-2 text-xs">
          <LanguageBadge language={repo.language} iconSize="sm" />
          <span className="rounded-md border border-dashed border-border/60 bg-muted/30 px-2 py-0.5 font-mono text-[11px] text-muted-foreground/60">
            Not yet indexed
          </span>
        </div>
      )}

      <div className="mt-8 grid w-full grid-cols-1 gap-3 sm:grid-cols-2 text-left">
        {STARTER_CARDS.map((card) => {
          const Icon = card.icon;
          return (
            <button
              key={card.title}
              onClick={() => onSelectPrompt(card.prompt)}
              className="group flex flex-col items-start rounded-2xl border border-dashed border-border/80 bg-card/60 p-4 transition-all hover:-translate-y-0.5 hover:border-primary/50 hover:bg-card hover:shadow-md"
            >
              <div className="flex items-center gap-2 text-sm font-semibold text-foreground group-hover:text-primary transition-colors">
                <div className="flex size-7 items-center justify-center rounded-lg bg-primary/10 text-primary">
                  <Icon className="size-3.5" />
                </div>
                <span>{card.title}</span>
              </div>
              <p className="mt-2 text-xs text-muted-foreground leading-relaxed">
                {card.description}
              </p>
            </button>
          );
        })}
      </div>
    </div>
  );
}

