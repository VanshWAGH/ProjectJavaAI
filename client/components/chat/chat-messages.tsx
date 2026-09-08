"use client";

import { useEffect, useRef, useState } from "react";
import { Check, Copy, Sparkles, User as UserIcon } from "lucide-react";
import type { ChatMessage, Repository, User } from "@/lib/api";
import { DevPilotIcon } from "@/components/icons/devpilot-icon";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { CitationList } from "@/components/chat/citation-card";
import { MarkdownRenderer } from "@/components/chat/markdown-renderer";
import { cn } from "@/lib/utils";

export function ChatMessages({
  messages,
  streaming,
  streamText,
  currentUser,
  repo,
}: {
  messages: ChatMessage[];
  streaming?: boolean;
  streamText?: string;
  currentUser?: User;
  repo?: Repository;
}) {
  const bottomRef = useRef<HTMLDivElement>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, streamText, streaming]);

  const handleCopyMessage = async (id: string, text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedId(id);
      setTimeout(() => setCopiedId(null), 2000);
    } catch {
      // ignore
    }
  };

  const formatTime = (isoString?: string) => {
    if (!isoString) return "";
    try {
      return new Date(isoString).toLocaleTimeString([], {
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch {
      return "";
    }
  };

  return (
    <div className="flex flex-1 flex-col overflow-y-auto px-4 py-6 md:px-6">
      <div className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-6">
        {messages.map((message) => {
          const isUser = message.role === "USER";

          return (
            <div
              key={message.id}
              className={cn(
                "group relative flex w-full gap-3",
                isUser ? "flex-row-reverse" : "flex-row"
              )}
            >
              {/* Avatar */}
              <div className="shrink-0 pt-0.5">
                {isUser ? (
                  <Avatar className="size-8 rounded-lg border border-border shadow-xs">
                    <AvatarImage
                      src={currentUser?.avatarUrl ?? undefined}
                      alt={currentUser?.displayName}
                    />
                    <AvatarFallback className="rounded-lg bg-primary/10 text-[11px] font-semibold text-primary">
                      {(currentUser?.displayName ?? "U").slice(0, 2).toUpperCase()}
                    </AvatarFallback>
                  </Avatar>
                ) : (
                  <div className="flex size-8 items-center justify-center rounded-lg bg-muted/80 border border-border shadow-xs">
                    <DevPilotIcon className="size-6" />
                  </div>
                )}
              </div>

              {/* Message Body */}
              <div
                className={cn(
                  "flex min-w-0 max-w-[85%] flex-col sm:max-w-[75%]",
                  isUser ? "items-end" : "items-start"
                )}
              >
                {/* Header */}
                <div className="flex items-center gap-2 px-1 text-[11px] text-muted-foreground mb-1">
                  <span className="font-medium">
                    {isUser ? currentUser?.displayName || "You" : "DevPilot"}
                  </span>
                  {message.createdAt && (
                    <span>{formatTime(message.createdAt)}</span>
                  )}
                </div>

                {/* Bubble */}
                <div
                  className={cn(
                    "relative overflow-hidden rounded-2xl p-4 text-sm leading-relaxed shadow-sm transition-all",
                    isUser
                      ? "rounded-tr-xs bg-primary text-primary-foreground selection:bg-background selection:text-foreground"
                      : "rounded-tl-xs border border-border/70 bg-card/90 text-card-foreground backdrop-blur-md"
                  )}
                >
                  {isUser ? (
                    <p className="whitespace-pre-wrap break-words">
                      {message.content}
                    </p>
                  ) : (
                    <>
                      <MarkdownRenderer content={message.content} />
                      <CitationList
                        citations={message.citations}
                        githubUrl={repo?.htmlUrl}
                        defaultBranch={repo?.defaultBranch}
                      />
                    </>
                  )}
                </div>

                {/* Footer Action Bar (Copy) */}
                {!isUser && (
                  <div className="mt-1 flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                    <Button
                      variant="ghost"
                      size="icon-xs"
                      onClick={() => handleCopyMessage(message.id, message.content)}
                      className="size-6 text-muted-foreground hover:text-foreground"
                      title="Copy message"
                    >
                      {copiedId === message.id ? (
                        <Check className="size-3 text-emerald-500" />
                      ) : (
                        <Copy className="size-3" />
                      )}
                    </Button>
                  </div>
                )}
              </div>
            </div>
          );
        })}

        {/* Streaming message bubble */}
        {streaming && (
          <div className="flex w-full gap-3">
            <div className="shrink-0 pt-0.5">
              <div className="flex size-8 items-center justify-center rounded-lg bg-muted/80 border border-border shadow-xs animate-pulse">
                <DevPilotIcon className="size-6" />
              </div>
            </div>

            <div className="flex min-w-0 max-w-[85%] flex-col items-start sm:max-w-[75%]">
              <div className="flex items-center gap-2 px-1 text-[11px] text-muted-foreground mb-1">
                <span className="font-medium">DevPilot</span>
                <span className="flex items-center gap-1 text-primary">
                  <Sparkles className="size-3 animate-spin" />
                  Generating…
                </span>
              </div>

              <div className="relative overflow-hidden rounded-2xl rounded-tl-xs border border-border/70 bg-card/90 p-4 text-sm leading-relaxed text-card-foreground shadow-sm backdrop-blur-md">
                {streamText ? (
                  <MarkdownRenderer content={streamText} />
                ) : (
                  <div className="flex items-center gap-2 text-muted-foreground">
                    <span className="inline-block size-2 animate-ping rounded-full bg-primary" />
                    <span className="text-xs">Analyzing codebase context…</span>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        <div ref={bottomRef} className="h-1" />
      </div>
    </div>
  );
}
