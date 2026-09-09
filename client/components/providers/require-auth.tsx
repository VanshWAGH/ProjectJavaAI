"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";

import { useCurrentUser } from "@/hooks/use-auth";
import { Spinner } from "@/components/ui/spinner";

export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { data: user, isLoading, isError } = useCurrentUser();
  const router = useRouter();

  useEffect(() => {
    if (!isLoading && !user) {
      // Clear the auth cookie so middleware doesn't block the redirect to /login
      document.cookie = "devpilot_auth=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
      router.replace("/login");
    }
  }, [isLoading, user, router]);

  if (isLoading) {
    return (
      <div className="flex min-h-svh items-center justify-center">
        <div className="flex flex-col items-center gap-3 text-muted-foreground">
          <Spinner className="size-6" />
          <p className="text-sm">Loading your workspace…</p>
        </div>
      </div>
    );
  }

  if (isError || !user) {
    return null;
  }

  return <>{children}</>;
}