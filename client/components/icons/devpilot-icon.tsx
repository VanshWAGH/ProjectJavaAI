import { cn } from "@/lib/utils";

export function DevPilotIcon({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 48 48"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={cn("size-8 select-none", className)}
      aria-label="DevPilot logo"
    >
      <defs>
        <linearGradient id="dp-gradient" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#3B82F6" />
          <stop offset="50%" stopColor="#6366F1" />
          <stop offset="100%" stopColor="#8B5CF6" />
        </linearGradient>
        <linearGradient id="dp-accent" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#38BDF8" />
          <stop offset="100%" stopColor="#A855F7" />
        </linearGradient>
      </defs>
      <rect width="48" height="48" rx="12" fill="url(#dp-gradient)" />
      {/* Pilot Code Wing / Compass Shape */}
      <path
        d="M24 10L36 34L24 28L12 34L24 10Z"
        fill="white"
        fillOpacity="0.95"
      />
      {/* Center AI Core Orb */}
      <circle cx="24" cy="23" r="3.5" fill="url(#dp-accent)" />
    </svg>
  );
}

export default DevPilotIcon;
