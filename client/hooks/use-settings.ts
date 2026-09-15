"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, type UserSettingsRequest } from "@/lib/api";
import { toast } from "@/components/ui/toast";

export const settingsQueryKeys = {
  all: ["settings"] as const,
};

export function useSettings() {
  return useQuery({
    queryKey: settingsQueryKeys.all,
    queryFn: () => api.getSettings(),
  });
}

export function useUpdateSettings() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (settings: UserSettingsRequest) => api.updateSettings(settings),
    onSuccess: (data) => {
      queryClient.setQueryData(settingsQueryKeys.all, data);
      toast.add({
        title: "Settings updated",
        description: "Your AI configuration has been saved.",
        type: "success",
      });
    },
    onError: (error: Error) => {
      toast.add({
        title: "Failed to update settings",
        description: error.message,
        type: "error",
      });
    },
  });
}
