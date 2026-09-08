import Link from "next/link";
import { useEffect, useState } from "react";
import { AlertCircle, FolderGit2, Loader2, Sparkles } from "lucide-react";
import { useCurrentUser } from "@/hooks/use-auth";
import {
  useChatMessages,
  useChatSessions,
  useCreateChatSession,
  useStreamChat,
} from "@/hooks/use-chat";
import { useRepository } from "@/hooks/use-repos";
import { ChatEmptyState } from "@/components/chat/chat-empty-state";
import { ChatHeader } from "@/components/chat/chat-header";
import { ChatInput } from "@/components/chat/chat-input";
import { ChatMessages } from "@/components/chat/chat-messages";
import { ChatSessionsSidebar } from "@/components/chat/chat-sessions-sidebar";
import { IndexErrorAlert } from "@/components/dashboard/index-error-alert";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button, buttonVariants } from "@/components/ui/button";
import { Sheet, SheetContent } from "@/components/ui/sheet";
import { Progress } from "@/components/ui/progress";
import { cn } from "@/lib/utils";

export function ChatView({ repoId }: { repoId: string }) {
  const { data: currentUser } = useCurrentUser();
  const repoQuery = useRepository(repoId);
  const sessionsQuery = useChatSessions(repoId);
  const createSessionMutation = useCreateChatSession(repoId);

  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [desktopSidebarOpen, setDesktopSidebarOpen] = useState(true);

  const sessions = sessionsQuery.data ?? [];

  // Automatically select the first/most recent session when sessions load
  useEffect(() => {
    if (sessions.length > 0 && !activeSessionId) {
      setActiveSessionId(sessions[0].id);
    }
  }, [sessions, activeSessionId]);

  const messagesQuery = useChatMessages(activeSessionId);
  const messages = messagesQuery.data ?? [];

  const { send, stop, streaming, streamText } = useStreamChat(activeSessionId);

  const handleNewSession = async () => {
    try {
      const newSession = await createSessionMutation.mutateAsync(
        repoQuery.data ? `${repoQuery.data.name} Chat` : undefined
      );
      setActiveSessionId(newSession.id);
      setMobileSidebarOpen(false);
    } catch {
      // Error handled by mutation toast
    }
  };

  const handleSendMessage = async (text: string) => {
    let targetSessionId = activeSessionId;

    // If no session exists yet, create one first!
    if (!targetSessionId) {
      try {
        const firstSentence = text.length > 30 ? `${text.substring(0, 30)}…` : text;
        const newSession = await createSessionMutation.mutateAsync(firstSentence);
        targetSessionId = newSession.id;
        setActiveSessionId(newSession.id);
      } catch {
        return;
      }
    }

    if (targetSessionId) {
      void send(text);
    }
  };

  const repo = repoQuery.data;
  const isIndexing = repo?.indexStatus === "INDEXING";
  const isFailed = repo?.indexStatus === "FAILED";

  const progressPercent =
    repo?.filesTotal && repo.filesTotal > 0
      ? Math.round(((repo.filesProcessed || 0) / repo.filesTotal) * 100)
      : 0;

  if (repoQuery.isLoading) {
    return (
      <div className="flex min-h-svh flex-col items-center justify-center gap-3 bg-background">
        <Loader2 className="size-8 animate-spin text-primary" />
        <p className="text-sm text-muted-foreground">Loading repository details…</p>
      </div>
    );
  }

  if (repoQuery.isError || !repo) {
    return (
      <div className="flex min-h-svh flex-col items-center justify-center gap-4 bg-background p-4 text-center">
        <FolderGit2 className="size-12 text-muted-foreground" />
        <h2 className="text-lg font-semibold">Repository not found</h2>
        <p className="max-w-sm text-xs text-muted-foreground">
          {(repoQuery.error as Error)?.message ??
            "Could not load the requested repository. It may have been removed or you may lack permissions."}
        </p>
        <Link
          href="/dashboard"
          className={cn(buttonVariants({ variant: "default", size: "sm" }))}
        >
          Back to Dashboard
        </Link>
      </div>
    );
  }

  return (
    <div className="flex h-svh flex-col overflow-hidden bg-background">
      {/* Top Header */}
      <ChatHeader
        repo={repo}
        onToggleSidebar={() => {
          setMobileSidebarOpen(true);
          setDesktopSidebarOpen((prev) => !prev);
        }}
        onNewChat={handleNewSession}
        hasSessions={sessions.length > 0}
      />

      {/* Indexing status warning or banner */}
      {isIndexing && (
        <div className="flex items-center justify-between border-b border-amber-500/20 bg-amber-500/10 px-4 py-2 text-xs text-amber-600 dark:text-amber-400">
          <div className="flex items-center gap-2">
            <Loader2 className="size-3.5 animate-spin" />
            <span>
              Indexing in progress: {repo.filesProcessed || 0} / {repo.filesTotal || 0} files processed ({progressPercent}%). Answers will improve as indexing finishes.
            </span>
          </div>
          <div className="w-24 sm:w-36">
            <Progress value={progressPercent} className="h-1.5" />
          </div>
        </div>
      )}

      {isFailed && (
        <div className="p-4 pb-0">
          <IndexErrorAlert message={repo.errorMessage ?? "Indexing failed. Please retry."} />
        </div>
      )}

      {/* Main Chat Workspace */}
      <div className="flex flex-1 min-h-0 overflow-hidden">
        {/* Desktop Sidebar */}
        {desktopSidebarOpen && (
          <div className="hidden md:block h-full shrink-0">
            <ChatSessionsSidebar
              sessions={sessions}
              activeSessionId={activeSessionId}
              onSelectSession={(id) => setActiveSessionId(id)}
              onNewSession={handleNewSession}
              isCreating={createSessionMutation.isPending}
            />
          </div>
        )}

        {/* Mobile Sheet Sidebar */}
        <Sheet open={mobileSidebarOpen} onOpenChange={setMobileSidebarOpen}>
          <SheetContent side="left" className="p-0 w-72">
            <ChatSessionsSidebar
              sessions={sessions}
              activeSessionId={activeSessionId}
              onSelectSession={(id) => {
                setActiveSessionId(id);
                setMobileSidebarOpen(false);
              }}
              onNewSession={handleNewSession}
              isCreating={createSessionMutation.isPending}
              className="border-none"
            />
          </SheetContent>
        </Sheet>

        {/* Chat Messages + Input Column */}
        <div className="flex flex-1 min-w-0 flex-col overflow-hidden">
          {messages.length === 0 && !streaming ? (
            <div className="flex flex-1 items-center justify-center overflow-y-auto">
              <ChatEmptyState
                repo={repo}
                onSelectPrompt={handleSendMessage}
              />
            </div>
          ) : (
            <ChatMessages
              messages={messages}
              streaming={streaming}
              streamText={streamText}
              currentUser={currentUser}
              repo={repo}
            />
          )}

          {/* Sticky Bottom Input Bar */}
          <div className="border-t border-border/50 bg-background/80 backdrop-blur-md">
            <ChatInput
              onSend={handleSendMessage}
              onStop={stop}
              streaming={streaming}
              disabled={createSessionMutation.isPending}
              placeholder={`Ask anything about ${repo.name}…`}
            />
          </div>
        </div>
      </div>
    </div>
  );
}

export default ChatView;
