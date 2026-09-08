"use client";

import { useState, useRef, useEffect } from "react";
import { ArrowUp, Square, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

const QUICK_PROMPTS = [
  "Explain the project architecture",
  "Where is authentication handled?",
  "List key API endpoints",
  "How to set up and run locally?",
];

export function ChatInput({
  onSend,
  onStop,
  streaming,
  disabled,
  placeholder = "Ask DevPilot anything about this repository…",
}: {
  onSend: (message: string) => void;
  onStop?: () => void;
  streaming?: boolean;
  disabled?: boolean;
  placeholder?: string;
}) {
  const [content, setContent] = useState("");
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = "auto";
      textareaRef.current.style.height = `${Math.min(
        textareaRef.current.scrollHeight,
        200
      )}px`;
    }
  }, [content]);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSubmit();
    }
  };

  const handleSubmit = () => {
    const trimmed = content.trim();
    if (!trimmed || streaming || disabled) return;
    onSend(trimmed);
    setContent("");
    if (textareaRef.current) {
      textareaRef.current.style.height = "auto";
    }
  };

  const handlePromptClick = (prompt: string) => {
    if (streaming || disabled) return;
    onSend(prompt);
  };

  return (
    <div className="mx-auto w-full max-w-4xl p-3 md:p-4">
      <div className="relative flex flex-col rounded-2xl border border-border/80 bg-card/90 shadow-lg shadow-foreground/5 backdrop-blur-xl transition-all focus-within:border-primary/50 focus-within:ring-2 focus-within:ring-primary/20">
        <textarea
          ref={textareaRef}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
          disabled={disabled}
          rows={1}
          className="w-full resize-none bg-transparent px-4 py-3.5 text-sm text-foreground placeholder:text-muted-foreground/70 focus:outline-none disabled:opacity-50"
        />

        <div className="flex items-center justify-between border-t border-border/40 px-3 py-2 text-xs text-muted-foreground">
          <div className="hidden sm:flex items-center gap-1.5 font-mono text-[11px] text-muted-foreground/80">
            <span>Press</span>
            <kbd className="rounded border border-border/70 bg-muted px-1.5 py-0.5 text-[10px]">
              Enter ↵
            </kbd>
            <span>to send</span>
          </div>

          <div className="flex w-full sm:w-auto items-center justify-end gap-2">
            {streaming ? (
              <Button
                type="button"
                variant="destructive"
                size="sm"
                onClick={onStop}
                className="h-8 gap-1.5 rounded-xl text-xs font-medium"
              >
                <Square className="size-3 fill-current" />
                <span>Stop</span>
              </Button>
            ) : (
              <Button
                type="button"
                variant="default"
                size="sm"
                disabled={!content.trim() || disabled}
                onClick={handleSubmit}
                className="h-8 gap-1.5 rounded-xl text-xs font-medium shadow-sm transition-all"
              >
                <span>Send</span>
                <ArrowUp className="size-3.5" />
              </Button>
            )}
          </div>
        </div>
      </div>

      <div className="mt-2 hidden sm:flex items-center gap-1.5 overflow-x-auto py-1 scrollbar-none">
        <Sparkles className="size-3.5 shrink-0 text-primary" />
        <span className="shrink-0 text-[11px] font-medium text-muted-foreground">
          Suggestions:
        </span>
        {QUICK_PROMPTS.map((prompt) => (
          <button
            key={prompt}
            onClick={() => handlePromptClick(prompt)}
            disabled={streaming || disabled}
            className="shrink-0 rounded-full border border-border/70 bg-background/50 px-2.5 py-1 text-[11px] text-muted-foreground transition-colors hover:border-primary/40 hover:bg-muted hover:text-foreground disabled:opacity-50"
          >
            {prompt}
          </button>
        ))}
      </div>
    </div>
  );
}
