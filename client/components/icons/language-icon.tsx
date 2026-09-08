import { FileCode, Terminal, Database, Globe, Cpu } from "lucide-react";
import { cn } from "@/lib/utils";

export type LanguageIconProps = {
  language: string | null;
  size?: "sm" | "md" | "lg";
  className?: string;
};

const LANGUAGE_CONFIG: Record<
  string,
  { label: string; color: string; bg: string; border: string }
> = {
  typescript: {
    label: "TypeScript",
    color: "#3178C6",
    bg: "rgba(49, 120, 198, 0.12)",
    border: "rgba(49, 120, 198, 0.3)",
  },
  javascript: {
    label: "JavaScript",
    color: "#F7DF1E",
    bg: "rgba(247, 223, 30, 0.12)",
    border: "rgba(247, 223, 30, 0.3)",
  },
  python: {
    label: "Python",
    color: "#3776AB",
    bg: "rgba(55, 118, 171, 0.12)",
    border: "rgba(55, 118, 171, 0.3)",
  },
  java: {
    label: "Java",
    color: "#EA2D2E",
    bg: "rgba(234, 45, 46, 0.12)",
    border: "rgba(234, 45, 46, 0.3)",
  },
  go: {
    label: "Go",
    color: "#00ADD8",
    bg: "rgba(0, 173, 216, 0.12)",
    border: "rgba(0, 173, 216, 0.3)",
  },
  rust: {
    label: "Rust",
    color: "#DEA584",
    bg: "rgba(222, 165, 132, 0.12)",
    border: "rgba(222, 165, 132, 0.3)",
  },
  cpp: {
    label: "C++",
    color: "#00599C",
    bg: "rgba(0, 89, 156, 0.12)",
    border: "rgba(0, 89, 156, 0.3)",
  },
  "c++": {
    label: "C++",
    color: "#00599C",
    bg: "rgba(0, 89, 156, 0.12)",
    border: "rgba(0, 89, 156, 0.3)",
  },
  c: {
    label: "C",
    color: "#A8B9CC",
    bg: "rgba(168, 185, 204, 0.12)",
    border: "rgba(168, 185, 204, 0.3)",
  },
  csharp: {
    label: "C#",
    color: "#239120",
    bg: "rgba(35, 145, 32, 0.12)",
    border: "rgba(35, 145, 32, 0.3)",
  },
  "c#": {
    label: "C#",
    color: "#239120",
    bg: "rgba(35, 145, 32, 0.12)",
    border: "rgba(35, 145, 32, 0.3)",
  },
  ruby: {
    label: "Ruby",
    color: "#CC342D",
    bg: "rgba(204, 52, 45, 0.12)",
    border: "rgba(204, 52, 45, 0.3)",
  },
  php: {
    label: "PHP",
    color: "#777BB4",
    bg: "rgba(119, 123, 180, 0.12)",
    border: "rgba(119, 123, 180, 0.3)",
  },
  kotlin: {
    label: "Kotlin",
    color: "#7F52FF",
    bg: "rgba(127, 82, 255, 0.12)",
    border: "rgba(127, 82, 255, 0.3)",
  },
  swift: {
    label: "Swift",
    color: "#F05138",
    bg: "rgba(240, 81, 56, 0.12)",
    border: "rgba(240, 81, 56, 0.3)",
  },
  html: {
    label: "HTML",
    color: "#E34F26",
    bg: "rgba(227, 79, 38, 0.12)",
    border: "rgba(227, 79, 38, 0.3)",
  },
  css: {
    label: "CSS",
    color: "#1572B6",
    bg: "rgba(21, 114, 182, 0.12)",
    border: "rgba(21, 114, 182, 0.3)",
  },
  shell: {
    label: "Shell",
    color: "#89E051",
    bg: "rgba(137, 224, 81, 0.12)",
    border: "rgba(137, 224, 81, 0.3)",
  },
  bash: {
    label: "Bash",
    color: "#89E051",
    bg: "rgba(137, 224, 81, 0.12)",
    border: "rgba(137, 224, 81, 0.3)",
  },
  sql: {
    label: "SQL",
    color: "#E38C00",
    bg: "rgba(227, 140, 0, 0.12)",
    border: "rgba(227, 140, 0, 0.3)",
  },
};

export function getLanguageLabel(language: string | null): string {
  if (!language) return "Code";
  const normalized = language.toLowerCase().trim();
  return LANGUAGE_CONFIG[normalized]?.label ?? language;
}

export function getLanguageColor(language: string | null): string {
  if (!language) return "#6B7280";
  const normalized = language.toLowerCase().trim();
  return LANGUAGE_CONFIG[normalized]?.color ?? "#6B7280";
}

const sizeClasses = {
  sm: "size-4 text-[10px]",
  md: "size-6 text-xs",
  lg: "size-8 text-sm",
};

const iconSizes = {
  sm: 12,
  md: 14,
  lg: 18,
};

export function LanguageIcon({
  language,
  size = "md",
  className,
}: LanguageIconProps) {
  const normalized = language?.toLowerCase().trim() ?? "";
  const config = LANGUAGE_CONFIG[normalized];
  const color = config?.color ?? "#8B5CF6";
  const bg = config?.bg ?? "rgba(139, 92, 246, 0.12)";
  const border = config?.border ?? "rgba(139, 92, 246, 0.3)";
  const pxSize = iconSizes[size];

  const renderIcon = () => {
    switch (normalized) {
      case "shell":
      case "bash":
        return <Terminal size={pxSize} style={{ color }} />;
      case "sql":
        return <Database size={pxSize} style={{ color }} />;
      case "html":
      case "css":
        return <Globe size={pxSize} style={{ color }} />;
      case "rust":
      case "c":
      case "c++":
      case "cpp":
        return <Cpu size={pxSize} style={{ color }} />;
      default:
        return <FileCode size={pxSize} style={{ color }} />;
    }
  };

  return (
    <div
      className={cn(
        "relative flex shrink-0 items-center justify-center rounded-lg border font-mono font-bold shadow-xs transition-transform",
        sizeClasses[size],
        className
      )}
      style={{
        backgroundColor: bg,
        borderColor: border,
      }}
      title={getLanguageLabel(language)}
    >
      {renderIcon()}
    </div>
  );
}

export default LanguageIcon;
