"use client";

import React, { useState } from "react";
import { BrainCircuit, ChevronDown } from "lucide-react";
import { CodeBlock } from "@/components/chat/code-block";
import { cn } from "@/lib/utils";

function ThinkingBlock({
  thinking,
  isLive,
}: {
  thinking: string;
  isLive: boolean;
}) {
  const [isOpen, setIsOpen] = useState(isLive);

  return (
    <div className="my-2.5 overflow-hidden rounded-xl border border-border/70 bg-muted/40 text-xs transition-all shadow-xs">
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="flex w-full items-center justify-between px-3 py-2 text-muted-foreground transition-colors hover:text-foreground hover:bg-muted/60"
      >
        <div className="flex items-center gap-2">
          <BrainCircuit
            className={cn(
              "size-3.5 text-primary",
              isLive && "animate-pulse text-amber-500"
            )}
          />
          <span className="font-medium text-[11px] uppercase tracking-wider">
            {isLive ? "Thinking..." : "Thought Process"}
          </span>
          {isLive && (
            <span className="inline-block size-1.5 animate-ping rounded-full bg-amber-500" />
          )}
        </div>
        <ChevronDown
          className={cn(
            "size-3.5 transition-transform duration-200",
            isOpen && "rotate-180"
          )}
        />
      </button>

      {isOpen && (
        <div className="border-t border-border/50 bg-background/50 px-3.5 py-2.5 font-mono text-[11px] leading-relaxed text-muted-foreground/90 whitespace-pre-wrap max-h-64 overflow-y-auto">
          {thinking}
        </div>
      )}
    </div>
  );
}

export function MarkdownRenderer({ content }: { content: string }) {
  if (!content) return null;

  // Extract <think>...</think> or unclosed <think>... during streaming
  const thinkRegex = /<think>([\s\S]*?)(?:<\/think>|$)/;
  const thinkMatch = content.match(thinkRegex);

  let thinkingContent: string | null = null;
  let isThinkingLive = false;
  let renderableContent = content;

  if (thinkMatch && thinkMatch[1]) {
    thinkingContent = thinkMatch[1].trim();
    isThinkingLive = !content.includes("</think>");
    renderableContent = content.replace(thinkRegex, "").trim();
  }

  // Split content by code blocks: ```lang ... ```
  const codeBlockRegex = /```(\w*)\n([\s\S]*?)```/g;
  const parts: React.ReactNode[] = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  let keyCounter = 0;

  if (renderableContent) {
    while ((match = codeBlockRegex.exec(renderableContent)) !== null) {
      const textBefore = renderableContent.substring(lastIndex, match.index);
      if (textBefore) {
        parts.push(
          <RenderText key={`text-${keyCounter++}`} text={textBefore} />
        );
      }

      const lang = match[1] || "";
      const code = match[2] || "";
      parts.push(
        <CodeBlock
          key={`code-${keyCounter++}`}
          language={lang}
          code={code.replace(/\n$/, "")}
        />
      );

      lastIndex = match.index + match[0].length;
    }

    const remaining = renderableContent.substring(lastIndex);
    if (remaining) {
      parts.push(
        <RenderText key={`text-${keyCounter++}`} text={remaining} />
      );
    }
  }

  return (
    <div className="space-y-2 text-sm leading-relaxed">
      {thinkingContent && (
        <ThinkingBlock thinking={thinkingContent} isLive={isThinkingLive} />
      )}
      {parts}
    </div>
  );
}

function RenderText({ text }: { text: string }) {
  const paragraphs = text.split(/\n\n+/);

  return (
    <>
      {paragraphs.map((para, pIdx) => {
        const trimmed = para.trim();
        if (!trimmed) return null;

        // Headers
        if (trimmed.startsWith("### ")) {
          return (
            <h4
              key={pIdx}
              className="mt-3 mb-1 text-sm font-semibold text-foreground tracking-tight"
            >
              {renderInline(trimmed.substring(4))}
            </h4>
          );
        }
        if (trimmed.startsWith("## ")) {
          return (
            <h3
              key={pIdx}
              className="mt-4 mb-1.5 text-base font-semibold text-foreground tracking-tight"
            >
              {renderInline(trimmed.substring(3))}
            </h3>
          );
        }
        if (trimmed.startsWith("# ")) {
          return (
            <h2
              key={pIdx}
              className="mt-5 mb-2 text-lg font-bold text-foreground tracking-tight"
            >
              {renderInline(trimmed.substring(2))}
            </h2>
          );
        }

        // Blockquote
        if (trimmed.startsWith("> ")) {
          const quoteLines = trimmed
            .split("\n")
            .map((l) => l.replace(/^>\s?/, ""))
            .join("\n");
          return (
            <blockquote
              key={pIdx}
              className="my-2 border-l-2 border-primary/50 pl-3 italic text-muted-foreground"
            >
              {renderInline(quoteLines)}
            </blockquote>
          );
        }

        // List
        const lines = trimmed.split("\n");
        const isBulletList = lines.every((l) => /^\s*[-*•]\s+/.test(l));
        const isNumberedList = lines.every((l) => /^\s*\d+\.\s+/.test(l));

        if (isBulletList) {
          return (
            <ul key={pIdx} className="my-2 list-disc space-y-1 pl-5">
              {lines.map((line, lIdx) => (
                <li key={lIdx} className="text-foreground/90">
                  {renderInline(line.replace(/^\s*[-*•]\s+/, ""))}
                </li>
              ))}
            </ul>
          );
        }

        if (isNumberedList) {
          return (
            <ol key={pIdx} className="my-2 list-decimal space-y-1 pl-5">
              {lines.map((line, lIdx) => (
                <li key={lIdx} className="text-foreground/90">
                  {renderInline(line.replace(/^\s*\d+\.\s+/, ""))}
                </li>
              ))}
            </ol>
          );
        }

        // Regular paragraph with single line-breaks converted to <br />
        return (
          <p key={pIdx} className="text-foreground/90">
            {lines.map((line, lIdx) => (
              <React.Fragment key={lIdx}>
                {renderInline(line)}
                {lIdx < lines.length - 1 && <br />}
              </React.Fragment>
            ))}
          </p>
        );
      })}
    </>
  );
}

function renderInline(str: string): React.ReactNode[] {
  // Matches inline code `code`, bold **text**, italic *text*, links [text](url)
  const tokenRegex =
    /(`([^`]+)`)|(\*\*([^*]+)\*\*)|(\*([^*]+)\*)|(\[([^\]]+)\]\(([^)]+)\))/g;

  const elements: React.ReactNode[] = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  let counter = 0;

  while ((match = tokenRegex.exec(str)) !== null) {
    if (match.index > lastIndex) {
      elements.push(str.substring(lastIndex, match.index));
    }

    if (match[1]) {
      // Inline code
      elements.push(
        <code
          key={counter++}
          className="rounded-md bg-muted px-1.5 py-0.5 font-mono text-[12px] font-medium text-foreground"
        >
          {match[2]}
        </code>
      );
    } else if (match[3]) {
      // Bold
      elements.push(
        <strong key={counter++} className="font-semibold text-foreground">
          {match[4]}
        </strong>
      );
    } else if (match[5]) {
      // Italic
      elements.push(
        <em key={counter++} className="italic">
          {match[6]}
        </em>
      );
    } else if (match[7]) {
      // Link
      elements.push(
        <a
          key={counter++}
          href={match[9]}
          target="_blank"
          rel="noopener noreferrer"
          className="font-medium text-primary underline underline-offset-2 hover:text-primary/80"
        >
          {match[8]}
        </a>
      );
    }

    lastIndex = match.index + match[0].length;
  }

  if (lastIndex < str.length) {
    elements.push(str.substring(lastIndex));
  }

  return elements;
}
