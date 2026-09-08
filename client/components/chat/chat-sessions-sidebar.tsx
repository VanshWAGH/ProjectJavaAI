"use client";

import { MessageSquare, Plus, Clock, Sparkles } from "lucide-react";
import type { ChatSession } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { cn } from "@/lib/utils";

export function ChatSessionsSidebar({
  sessions,
  activeSessionId,
  onSelectSession,
  onNewSession,
  isCreating,
  className,
}: {
  sessions: ChatSession[];
  activeSessionId: string | null;
  onSelectSession: (id: string) => void;
  onNewSession: () => void;
  isCreating?: boolean;
  className?: string;
}) {
  const formatDate = (iso: string) => {
    try {
      const date = new Date(iso);
      return date.toLocaleDateString(undefined, {
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch {
      return "";
    }
  };

  return (
    <div
      className={cn(
        "flex h-full w-72 flex-col border-r border-border/70 bg-card/40 backdrop-blur-md",
        className
      )}
    >
      <div className="flex h-14 items-center justify-between border-b border-border/70 px-4">
        <div className="flex items-center gap-2">
          <MessageSquare className="size-4 text-primary" />
          <span className="font-heading text-sm font-medium">Discussions</span>
          <span className="rounded-full bg-muted px-2 py-0.5 text-[11px] font-mono text-muted-foreground">
            {sessions.length}
          </span>
        </div>
        <Button
          variant="outline"
          size="icon-xs"
          onClick={onNewSession}
          disabled={isCreating}
          className="size-7"
          title="New Discussion"
        >
          <Plus className="size-3.5" />
        </Button>
      </div>

      <div className="p-3">
        <Button
          variant="default"
          size="sm"
          onClick={onNewSession}
          disabled={isCreating}
          className="w-full justify-center gap-2 text-xs font-medium shadow-sm"
        >
          <Sparkles className="size-3.5" />
          <span>New Discussion</span>
        </Button>
      </div>

      <ScrollArea className="flex-1 px-3">
        <div className="flex flex-col gap-1 pb-4">
          {sessions.length === 0 ? (
            <div className="py-8 text-center text-xs text-muted-foreground">
              <p>No past discussions.</p>
              <p className="mt-1 text-[11px]">Start a conversation below!</p>
            </div>
          ) : (
            sessions.map((session) => {
              const isActive = session.id === activeSessionId;
              return (
                <button
                  key={session.id}
                  onClick={() => onSelectSession(session.id)}
                  className={cn(
                    "group relative flex w-full flex-col items-start gap-1 rounded-xl p-2.5 text-left text-xs transition-all",
                    isActive
                      ? "bg-primary/10 text-primary font-medium border border-primary/20 shadow-xs"
                      : "text-foreground hover:bg-muted/70 hover:text-foreground"
                  )}
                >
                  <span className="line-clamp-1 w-full font-medium">
                    {session.title || "Untitled Discussion"}
                  </span>
                  <div className="flex items-center gap-1 text-[10px] text-muted-foreground">
                    <Clock className="size-3" />
                    <span>{formatDate(session.createdAt)}</span>
                  </div>
                </button>
              );
            })
          )}
        </div>
      </ScrollArea>
    </div>
  );
}
