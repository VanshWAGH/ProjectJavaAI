"use client";

import {
  Compass,
  FileCode2,
  GitPullRequest,
  ShieldCheck,
  Terminal,
  Zap,
} from "lucide-react";
import type { Repository } from "@/lib/api";
import { DevPilotIcon } from "@/components/icons/devpilot-icon";
import { LanguageBadge } from "@/components/dashboard/language-badge";

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
  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col items-center justify-center px-4 py-8 text-center sm:py-12">
      <div className="relative mb-4 flex size-16 items-center justify-center rounded-2xl bg-muted/60 shadow-md">
        <DevPilotIcon className="size-10" />
      </div>

      <h2 className="font-heading text-xl font-bold tracking-tight text-foreground sm:text-2xl">
        Chat with {repo ? repo.name : "Repository"}
      </h2>

      <p className="mt-2 max-w-md text-sm text-muted-foreground">
        DevPilot has indexed this codebase. Ask questions, explore logic, or locate functions with line-precise citations.
      </p>

      {repo && (
        <div className="mt-3 flex flex-wrap items-center justify-center gap-2 text-xs">
          <LanguageBadge language={repo.language} iconSize="sm" />
          <span className="rounded-md border border-border/60 bg-muted/40 px-2 py-0.5 font-mono text-[11px] text-muted-foreground">
            {repo.filesTotal || repo.filesProcessed || 0} files indexed
          </span>
          <span className="rounded-md border border-border/60 bg-muted/40 px-2 py-0.5 font-mono text-[11px] text-muted-foreground">
            {repo.chunkCount || 0} code chunks
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
