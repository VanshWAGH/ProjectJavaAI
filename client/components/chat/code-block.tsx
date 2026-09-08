"use client";

import { useState } from "react";
import { Check, Copy, Terminal } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export function CodeBlock({
  code,
  language,
  className,
}: {
  code: string;
  language?: string;
  className?: string;
}) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // ignore
    }
  };

  const lines = code.trim().split("\n");

  return (
    <div
      className={cn(
        "group relative my-3 overflow-hidden rounded-xl border border-border/80 bg-zinc-950 text-zinc-100 shadow-md dark:border-border/60",
        className
      )}
    >
      <div className="flex h-9 items-center justify-between border-b border-zinc-800 bg-zinc-900/90 px-3.5 text-xs text-zinc-400">
        <div className="flex items-center gap-2">
          <Terminal className="size-3.5 text-zinc-400" />
          <span className="font-mono text-[11px] uppercase tracking-wider text-zinc-300">
            {language || "code"}
          </span>
        </div>
        <Button
          variant="ghost"
          size="sm"
          onClick={handleCopy}
          className="h-6 gap-1 px-2 text-[11px] text-zinc-400 hover:bg-zinc-800 hover:text-zinc-100"
        >
          {copied ? (
            <>
              <Check className="size-3 text-emerald-400" />
              <span>Copied</span>
            </>
          ) : (
            <>
              <Copy className="size-3" />
              <span>Copy</span>
            </>
          )}
        </Button>
      </div>

      <div className="overflow-x-auto p-3.5 font-mono text-xs leading-relaxed">
        <pre className="grid">
          {lines.map((line, idx) => (
            <div key={idx} className="table-row">
              <span className="table-cell select-none pr-4 text-right text-zinc-600 font-mono text-[11px]">
                {idx + 1}
              </span>
              <span className="table-cell whitespace-pre">{line}</span>
            </div>
          ))}
        </pre>
      </div>
    </div>
  );
}
