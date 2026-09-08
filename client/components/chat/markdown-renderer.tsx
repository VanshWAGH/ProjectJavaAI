"use client";

import React from "react";
import { CodeBlock } from "@/components/chat/code-block";

export function MarkdownRenderer({ content }: { content: string }) {
  if (!content) return null;

  // Split content by code blocks: ```lang ... ```
  const codeBlockRegex = /```(\w*)\n([\s\S]*?)```/g;
  const parts: React.ReactNode[] = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  let keyCounter = 0;

  while ((match = codeBlockRegex.exec(content)) !== null) {
    const textBefore = content.substring(lastIndex, match.index);
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

  const remaining = content.substring(lastIndex);
  if (remaining) {
    parts.push(
      <RenderText key={`text-${keyCounter++}`} text={remaining} />
    );
  }

  return <div className="space-y-2 text-sm leading-relaxed">{parts}</div>;
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
