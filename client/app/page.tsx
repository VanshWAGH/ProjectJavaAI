"use client";

import Link from "next/link";
import { useEffect, useRef } from "react";
import {
  ArrowRight,
  Bot,
  BrainCircuit,
  Code2,
  GitMerge,
  MessageSquareCode,
  Search,
  Sparkles,
  Zap,
} from "lucide-react";
import { ModeToggle } from "@/components/ui/mode-toggle";
import { Button, buttonVariants } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { DevPilotIcon } from "@/components/icons/devpilot-icon";
import { GitHubIcon } from "@/components/icons/github-icon";
import { cn } from "@/lib/utils";
import { getGithubLoginUrl } from "@/lib/api";

const features = [
  {
    icon: BrainCircuit,
    title: "RAG-Powered Intelligence",
    description:
      "Spring AI + pgvector embeds every file in your repo and retrieves the most relevant code chunks to answer your questions with precision.",
    color: "text-violet-500",
    bg: "bg-violet-500/10",
    border: "border-violet-500/20",
  },
  {
    icon: MessageSquareCode,
    title: "Streaming Chat Interface",
    description:
      "Real-time token-by-token responses with code highlighting, copy buttons, and inline source citations that link back to exact file locations.",
    color: "text-sky-500",
    bg: "bg-sky-500/10",
    border: "border-sky-500/20",
  },
  {
    icon: Search,
    title: "Deep Codebase Search",
    description:
      "Ask in natural language — DevPilot searches across hundreds of files, functions, and classes to surface exactly what you need.",
    color: "text-emerald-500",
    bg: "bg-emerald-500/10",
    border: "border-emerald-500/20",
  },
  {
    icon: GitMerge,
    title: "GitHub OAuth Integration",
    description:
      "Securely connect your GitHub account to instantly list and index any of your public or private repositories with one click.",
    color: "text-amber-500",
    bg: "bg-amber-500/10",
    border: "border-amber-500/20",
  },
  {
    icon: Zap,
    title: "Incremental Indexing",
    description:
      "Fast background indexing with live progress updates — start chatting immediately while the rest of your codebase indexes.",
    color: "text-rose-500",
    bg: "bg-rose-500/10",
    border: "border-rose-500/20",
  },
  {
    icon: Code2,
    title: "Multi-Language Support",
    description:
      "TypeScript, Python, Java, Go, Rust, C++, and more — DevPilot understands the context of any language your stack uses.",
    color: "text-cyan-500",
    bg: "bg-cyan-500/10",
    border: "border-cyan-500/20",
  },
];

const chatExamples = [
  { role: "user", text: "How does the authentication flow work?" },
  {
    role: "assistant",
    text: "The auth flow uses GitHub OAuth2. Spring Security intercepts `/oauth2/authorization/github`, redirects to GitHub, then the `GithubOAuth2UserService` exchanges the code for a token and upserts the user. A session cookie `DEVPILOT_SESSION` is set on success.",
    citations: ["security/GithubOAuth2UserService.java:45–72", "config/SecurityConfig.java:18–41"],
  },
  { role: "user", text: "Where is the vector embedding created?" },
  {
    role: "assistant",
    text: "Embeddings are created in `IndexingService.indexRepository()`. Each file chunk is transformed into a `Document`, then batch-added to the `PgVectorStore` which calls the OpenAI embedding API under the hood.",
    citations: ["services/IndexingService.java:98–134"],
  },
];

function AnimatedGradient() {
  return (
    <div className="pointer-events-none absolute inset-0 overflow-hidden" aria-hidden>
      <div className="absolute -top-40 left-1/2 h-[600px] w-[700px] -translate-x-1/2 rounded-full bg-primary/20 blur-[120px] dark:bg-primary/10" />
      <div className="absolute bottom-0 left-0 h-[400px] w-[400px] rounded-full bg-violet-500/10 blur-[100px]" />
      <div className="absolute bottom-0 right-0 h-[400px] w-[400px] rounded-full bg-sky-500/10 blur-[100px]" />
    </div>
  );
}

function ChatPreview() {
  return (
    <div className="relative mx-auto w-full max-w-2xl overflow-hidden rounded-2xl border border-border/60 bg-card/80 shadow-2xl shadow-foreground/10 backdrop-blur-xl">
      {/* Window chrome */}
      <div className="flex items-center gap-1.5 border-b border-border/60 bg-muted/30 px-4 py-3">
        <span className="size-3 rounded-full bg-red-400/80" />
        <span className="size-3 rounded-full bg-amber-400/80" />
        <span className="size-3 rounded-full bg-emerald-400/80" />
        <span className="ml-3 text-xs text-muted-foreground">DevPilot — my-backend-repo</span>
      </div>

      {/* Chat messages */}
      <div className="space-y-4 p-4">
        {chatExamples.map((msg, i) => (
          <div
            key={i}
            className={cn(
              "flex gap-3",
              msg.role === "user" ? "flex-row-reverse" : "flex-row"
            )}
          >
            {/* Avatar */}
            <div
              className={cn(
                "flex size-7 shrink-0 items-center justify-center rounded-full text-xs",
                msg.role === "user"
                  ? "bg-primary text-primary-foreground"
                  : "bg-foreground text-background"
              )}
            >
              {msg.role === "user" ? "U" : <Bot className="size-4" />}
            </div>

            {/* Bubble */}
            <div
              className={cn(
                "max-w-[85%] space-y-2 rounded-2xl px-3.5 py-2.5 text-sm leading-relaxed",
                msg.role === "user"
                  ? "rounded-tr-sm bg-primary/15 text-foreground"
                  : "rounded-tl-sm bg-muted/60 text-foreground"
              )}
            >
              <p>{msg.text}</p>
              {msg.citations && (
                <div className="flex flex-wrap gap-1.5 pt-1">
                  {msg.citations.map((c, ci) => (
                    <span
                      key={ci}
                      className="inline-flex items-center gap-1 rounded-md border border-border/60 bg-background/60 px-2 py-0.5 font-mono text-[10px] text-muted-foreground"
                    >
                      <Code2 className="size-3" />
                      {c}
                    </span>
                  ))}
                </div>
              )}
            </div>
          </div>
        ))}
      </div>

      {/* Input */}
      <div className="border-t border-border/60 bg-muted/20 p-3">
        <div className="flex items-center gap-2 rounded-xl border border-border/60 bg-background/60 px-3 py-2">
          <Sparkles className="size-4 shrink-0 text-primary" />
          <span className="flex-1 text-sm text-muted-foreground">
            Ask anything about your codebase…
          </span>
          <ArrowRight className="size-4 text-muted-foreground" />
        </div>
      </div>
    </div>
  );
}

export default function Home() {
  return (
    <div className="relative flex min-h-svh flex-col overflow-hidden bg-background">
      <AnimatedGradient />

      {/* ── Navigation ── */}
      <header className="relative z-20 flex h-16 items-center justify-between border-b border-border/50 bg-background/60 px-6 backdrop-blur-md">
        <div className="flex items-center gap-2.5">
          <DevPilotIcon className="size-8 rounded-[10px]" />
          <span className="font-heading text-[1.05rem] font-semibold leading-none tracking-tight">
            DevPilot
          </span>
        </div>

        <nav className="flex items-center gap-3">
          <a
            href="https://github.com/VanshWAGH/ProjectJavaAI"
            target="_blank"
            rel="noopener noreferrer"
            className={cn(
              buttonVariants({ variant: "ghost", size: "sm" }),
              "gap-2"
            )}
          >
            <GitHubIcon className="size-4" />
            GitHub
          </a>
          <ModeToggle />
          <a
            href={getGithubLoginUrl()}
            className={cn(
              buttonVariants({ size: "sm" }),
              "gap-2 bg-foreground text-background hover:bg-foreground/90"
            )}
          >
            <GitHubIcon className="size-4" />
            Sign in
          </a>
        </nav>
      </header>

      {/* ── Hero ── */}
      <main className="relative z-10 flex flex-col items-center">
        <section className="flex w-full max-w-6xl flex-col items-center px-6 pb-16 pt-24 text-center">
          <Badge
            variant="outline"
            className="mb-6 gap-1.5 border-primary/30 bg-primary/5 px-3 py-1 text-xs font-medium text-primary"
          >
            <Sparkles className="size-3" />
            Spring AI · pgvector RAG · GitHub OAuth
          </Badge>

          <h1 className="max-w-3xl bg-gradient-to-br from-foreground via-foreground/90 to-foreground/50 bg-clip-text text-5xl font-extrabold leading-tight tracking-tight text-transparent sm:text-6xl lg:text-7xl">
            Chat with your
            <br />
            <span className="bg-gradient-to-r from-primary to-emerald-500 bg-clip-text text-transparent">
              codebase
            </span>
            , intelligently
          </h1>

          <p className="mt-6 max-w-2xl text-lg text-muted-foreground sm:text-xl">
            DevPilot indexes your GitHub repositories using vector embeddings and lets you
            have deep, contextual AI conversations about your code — with real source
            citations.
          </p>

          <div className="mt-10 flex flex-wrap items-center justify-center gap-4">
            <a
              href={getGithubLoginUrl()}
              className={cn(
                buttonVariants({ size: "lg" }),
                "h-12 gap-2 bg-foreground px-8 text-background hover:bg-foreground/90"
              )}
            >
              <GitHubIcon className="size-5" />
              Get started with GitHub
              <ArrowRight className="size-4" />
            </a>
            <Link
              href="/login"
              className={cn(
                buttonVariants({ variant: "outline", size: "lg" }),
                "h-12 px-8"
              )}
            >
              Sign in
            </Link>
          </div>

          {/* Stats row */}
          <div className="mt-14 flex flex-wrap items-center justify-center gap-8 text-sm text-muted-foreground">
            {[
              ["Open Source", "Spring AI + Next.js"],
              ["RAG Architecture", "pgvector semantic search"],
              ["Streaming Chat", "Real-time AI responses"],
            ].map(([title, subtitle]) => (
              <div key={title} className="flex flex-col items-center gap-0.5">
                <span className="font-semibold text-foreground">{title}</span>
                <span>{subtitle}</span>
              </div>
            ))}
          </div>
        </section>

        {/* ── Chat Preview ── */}
        <section className="w-full max-w-6xl px-6 pb-24">
          <ChatPreview />
        </section>

        {/* ── Features ── */}
        <section className="w-full bg-muted/30 py-24">
          <div className="mx-auto max-w-6xl px-6">
            <div className="mb-14 text-center">
              <h2 className="text-3xl font-bold tracking-tight sm:text-4xl">
                Everything you need to understand your codebase
              </h2>
              <p className="mt-3 text-muted-foreground">
                Built on proven open-source technologies — zero vendor lock-in.
              </p>
            </div>

            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {features.map((f) => (
                <div
                  key={f.title}
                  className={cn(
                    "group flex flex-col gap-4 rounded-2xl border bg-card/80 p-6 shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md",
                    f.border
                  )}
                >
                  <div
                    className={cn(
                      "flex size-11 items-center justify-center rounded-xl border",
                      f.bg,
                      f.border
                    )}
                  >
                    <f.icon className={cn("size-5", f.color)} />
                  </div>
                  <div>
                    <h3 className="font-semibold">{f.title}</h3>
                    <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">
                      {f.description}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* ── CTA ── */}
        <section className="w-full py-24">
          <div className="mx-auto flex max-w-2xl flex-col items-center gap-6 px-6 text-center">
            <div className="flex size-16 items-center justify-center rounded-2xl bg-foreground">
              <DevPilotIcon className="size-10 rounded-lg" />
            </div>
            <h2 className="text-3xl font-bold tracking-tight sm:text-4xl">
              Ready to explore your code?
            </h2>
            <p className="text-muted-foreground">
              Sign in with GitHub, pick a repository, and start asking questions. It's free
              and takes less than a minute to set up.
            </p>
            <a
              href={getGithubLoginUrl()}
              className={cn(
                buttonVariants({ size: "lg" }),
                "h-12 gap-2 bg-foreground px-10 text-background hover:bg-foreground/90"
              )}
            >
              <GitHubIcon className="size-5" />
              Start for free
              <ArrowRight className="size-4" />
            </a>
          </div>
        </section>
      </main>

      {/* ── Footer ── */}
      <footer className="relative z-10 border-t border-border/50 bg-background/60 py-8 text-center text-sm text-muted-foreground backdrop-blur-md">
        <div className="mx-auto flex max-w-6xl flex-col items-center gap-2 px-6 sm:flex-row sm:justify-between">
          <div className="flex items-center gap-2">
            <DevPilotIcon className="size-5 rounded-md" />
            <span>DevPilot — AI-powered codebase intelligence</span>
          </div>
          <a
            href="https://github.com/VanshWAGH/ProjectJavaAI"
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center gap-1.5 hover:text-foreground"
          >
            <GitHubIcon className="size-4" />
            View on GitHub
          </a>
        </div>
      </footer>
    </div>
  );
}
