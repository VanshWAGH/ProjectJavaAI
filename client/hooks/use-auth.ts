"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { api, type User } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";

export function useCurrentUser() {
  return useQuery<User>({
    queryKey: queryKeys.auth.me(),
    queryFn: () => api.me(),
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
}

export function useLogout() {
  const queryClient = useQueryClient();
  const router = useRouter();

  return useMutation({
    mutationFn: () => api.logout(),
    onSuccess: () => {
      document.cookie = "devpilot_auth=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
      queryClient.clear();
      router.push("/login");
    },
  });
}

export { useIsMobile } from "./use-mobile";