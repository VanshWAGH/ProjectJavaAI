"use client";

import Link from "next/link";
import {
  ArrowLeft,
  ExternalLink,
  GitBranch,
  MessageSquarePlus,
  PanelLeft,
  RotateCcw,
  Sparkles,
} from "lucide-react";
import type { Repository } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { LanguageBadge } from "@/components/dashboard/language-badge";
import { IndexStatusBadge } from "@/components/dashboard/repo-status";
import { ModeToggle } from "@/components/ui/mode-toggle";
import { useStartIndexing } from "@/hooks/use-repos";

export function ChatHeader({
  repo,
  onToggleSidebar,
  onNewChat,
  hasSessions,
}: {
  repo?: Repository;
  onToggleSidebar?: () => void;
  onNewChat?: () => void;
  hasSessions?: boolean;
}) {
  const indexMutation = useStartIndexing();
  const isIndexing =
    repo?.indexStatus === "INDEXING" || indexMutation.isPending;

  return (
    <header className="sticky top-0 z-20 flex h-14 shrink-0 items-center justify-between border-b border-border/70 bg-background/80 px-3 backdrop-blur-md md:px-4">
      <div className="flex min-w-0 items-center gap-2">
        <Link
          href="/dashboard"
          className="inline-flex size-8 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          title="Back to repositories"
        >
          <ArrowLeft className="size-4" />
        </Link>

        {onToggleSidebar && (
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={onToggleSidebar}
            className="text-muted-foreground hover:text-foreground"
            title="Toggle chat sessions"
          >
            <PanelLeft className="size-4" />
          </Button>
        )}

        <div className="flex min-w-0 items-center gap-2">
          {repo ? (
            <>
              <div className="flex min-w-0 items-baseline gap-1.5 truncate">
                <span className="truncate text-xs text-muted-foreground">
                  {repo.owner} /
                </span>
                <span className="truncate font-semibold text-sm text-foreground">
                  {repo.name}
                </span>
              </div>

              {repo.language && (
                <div className="hidden sm:block">
                  <LanguageBadge language={repo.language} iconSize="sm" />
                </div>
              )}

              <div className="hidden md:flex items-center gap-1 text-[11px] font-mono text-muted-foreground">
                <GitBranch className="size-3" />
                <span>{repo.defaultBranch}</span>
              </div>

              <div className="hidden lg:block">
                <IndexStatusBadge status={repo.indexStatus} />
              </div>
            </>
          ) : (
            <div className="h-4 w-32 animate-pulse rounded bg-muted" />
          )}
        </div>
      </div>

      <div className="flex items-center gap-1.5 sm:gap-2">
        <div className="hidden xl:flex items-center gap-1.5 rounded-full border border-border/70 bg-card/60 px-2.5 py-1 text-[11px] text-muted-foreground shadow-2xs backdrop-blur-xs">
          <Sparkles className="size-3 text-primary animate-pulse" />
          <span className="font-medium tracking-tight">OpenRouter Free AI</span>
        </div>

        {repo && repo.indexStatus === "INDEXING" && (
          <div className="hidden sm:flex items-center gap-2 rounded-full border border-amber-500/30 bg-amber-500/10 px-3 py-1 text-[11px]">
            <RotateCcw className="size-3 animate-spin text-amber-500" />
            <span className="font-medium text-amber-600 dark:text-amber-400">
              {repo.filesProcessed || 0}/{repo.filesTotal || "?"} files
            </span>
            {repo.filesTotal > 0 && (
              <span className="text-muted-foreground">
                ({Math.round(((repo.filesProcessed || 0) / repo.filesTotal) * 100)}%)
              </span>
            )}
          </div>
        )}

        {repo && repo.indexStatus !== "READY" && repo.indexStatus !== "INDEXING" && (
          <Button
            variant="outline"
            size="sm"
            disabled={indexMutation.isPending}
            onClick={() => indexMutation.mutate(repo.id)}
            className="h-8 gap-1.5 text-xs"
          >
            <RotateCcw className={`size-3.5 ${indexMutation.isPending ? "animate-spin" : ""}`} />
            <span className="hidden sm:inline">
              {indexMutation.isPending ? "Starting..." : "Re-index"}
            </span>
          </Button>
        )}

        {onNewChat && (
          <Button
            variant="outline"
            size="sm"
            onClick={onNewChat}
            className="h-8 gap-1.5 text-xs shadow-xs"
          >
            <MessageSquarePlus className="size-3.5 text-primary" />
            <span className="hidden sm:inline">New Chat</span>
          </Button>
        )}

        {repo?.htmlUrl && (
          <a
            href={repo.htmlUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex size-8 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            title="Open on GitHub"
          >
            <ExternalLink className="size-4" />
          </a>
        )}

        <ModeToggle />
      </div>
    </header>
  );
}
