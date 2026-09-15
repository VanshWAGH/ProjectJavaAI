package devPilot.backend.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import devPilot.backend.dto.ChatMessageResponse;
import devPilot.backend.dto.ChatSessionResponse;
import devPilot.backend.dto.CitationDto;
import devPilot.backend.dto.CreateChatSessionRequest;
import devPilot.backend.entity.ChatMessage;
import devPilot.backend.entity.ChatSession;
import devPilot.backend.entity.MessageRole;
import devPilot.backend.entity.Repository;
import devPilot.backend.entity.User;
import devPilot.backend.exception.NotFoundException;
import devPilot.backend.repository.ChatMessageRepository;
import devPilot.backend.repository.ChatSessionRepository;
import devPilot.backend.repository.RepositoryRepository;
import devPilot.backend.repository.UserRepository;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RepositoryRepository repositoryRepository;
    private final UserRepository userRepository;
    private final EmbeddingService embeddingService;
    private final ChatModel defaultChatModel;
    private final AiModelFactory aiModelFactory;
    private final ObjectMapper objectMapper;

    public ChatService(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            RepositoryRepository repositoryRepository,
            UserRepository userRepository,
            EmbeddingService embeddingService,
            ChatModel defaultChatModel,
            AiModelFactory aiModelFactory,
            ObjectMapper objectMapper) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.repositoryRepository = repositoryRepository;
        this.userRepository = userRepository;
        this.embeddingService = embeddingService;
        this.defaultChatModel = defaultChatModel;
        this.aiModelFactory = aiModelFactory;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ChatSessionResponse createSession(UUID userId, CreateChatSessionRequest request) {
        Repository repo = repositoryRepository.findByIdAndUserId(request.repositoryId(), userId)
                .orElseThrow(() -> new NotFoundException("Repository not found"));

        String title = (request.title() != null && !request.title().isBlank())
                ? request.title()
                : repo.getName() + " Discussion";

        ChatSession session = new ChatSession(userId, repo.getId(), title);
        session = chatSessionRepository.save(session);

        return new ChatSessionResponse(session.getId(), session.getRepositoryId(), session.getTitle(), session.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions(UUID userId, UUID repositoryId) {
        return chatSessionRepository.findByUserIdAndRepositoryIdOrderByCreatedAtDesc(userId, repositoryId)
                .stream()
                .map(s -> new ChatSessionResponse(s.getId(), s.getRepositoryId(), s.getTitle(), s.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(UUID userId, UUID sessionId) {
        chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NotFoundException("Chat session not found"));

        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    public SseEmitter streamReply(UUID userId, UUID sessionId, String userContent) {
        ChatSession session = chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NotFoundException("Chat session not found"));

        // ── Resolve the user's ChatModel (BYOK or server default) ──────────
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        ChatModel chatModel = aiModelFactory.getModelForUser(user, defaultChatModel);

        ChatMessage savedUserMsg = chatMessageRepository.save(
                new ChatMessage(sessionId, MessageRole.USER, userContent, null));

        SseEmitter emitter = new SseEmitter(180_000L);

        ChatMessageResponse userDto = toMessageResponse(savedUserMsg);
        try {
            emitter.send(SseEmitter.event()
                    .name("user_message")
                    .data(objectMapper.writeValueAsString(userDto)));
        } catch (Exception e) {
            emitter.completeWithError(e);
            return emitter;
        }

        CompletableFuture.runAsync(() -> {
            try {
                List<Document> similarDocs = embeddingService.searchSimilar(session.getRepositoryId(), userContent, 5);
                List<CitationDto> citations = new ArrayList<>();
                StringBuilder contextBuilder = new StringBuilder();

                for (Document doc : similarDocs) {
                    String filePath  = String.valueOf(doc.getMetadata().getOrDefault("filePath",  "unknown"));
                    String fileName  = String.valueOf(doc.getMetadata().getOrDefault("fileName",  filePath));
                    String repoOwner = String.valueOf(doc.getMetadata().getOrDefault("repoOwner", ""));
                    String indexedAt = String.valueOf(doc.getMetadata().getOrDefault("indexedAt", ""));
                    Integer startLine = doc.getMetadata().get("startLine") instanceof Number n ? n.intValue() : 1;
                    Integer endLine   = doc.getMetadata().get("endLine")   instanceof Number n ? n.intValue() : 1;
                    String language   = String.valueOf(doc.getMetadata().getOrDefault("language", "Code"));

                    citations.add(new CitationDto(filePath, fileName, startLine, endLine, language, repoOwner, indexedAt));

                    contextBuilder.append(String.format("--- File: %s (lines %d-%d, language: %s) ---\n",
                            filePath, startLine, endLine, language));
                    contextBuilder.append(doc.getText()).append("\n\n");
                }

                String systemPromptText = buildSystemPrompt(contextBuilder.toString());

                List<Message> promptMessages = new ArrayList<>();
                promptMessages.add(new SystemMessage(systemPromptText));

                // ── Conversation history (capped at last 10 turns) ─────────
                List<ChatMessage> history = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
                int historyStart = Math.max(0, history.size() - 20); // last 10 pairs
                for (int i = historyStart; i < history.size(); i++) {
                    ChatMessage m = history.get(i);
                    if (m.getId().equals(savedUserMsg.getId())) continue;
                    if (m.getRole() == MessageRole.USER) {
                        promptMessages.add(new UserMessage(m.getContent()));
                    } else {
                        promptMessages.add(new AssistantMessage(m.getContent()));
                    }
                }
                promptMessages.add(new UserMessage(userContent));

                Prompt prompt = new Prompt(promptMessages);
                StringBuilder fullResponse = new StringBuilder();

                chatModel.stream(prompt).toStream().forEach(chatResponse -> {
                    if (chatResponse != null && chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                        String text = chatResponse.getResult().getOutput().getText();
                        if (text != null && !text.isEmpty()) {
                            fullResponse.append(text);
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("token")
                                        .data(objectMapper.writeValueAsString(text)));
                            } catch (Exception ex) {
                                log.warn("Client disconnected during streaming: {}", ex.getMessage());
                                throw new RuntimeException(ex);
                            }
                        }
                    }
                });

                String finalAssistantText = fullResponse.toString();
                String citationsJson = objectMapper.writeValueAsString(citations);

                ChatMessage assistantMsg = new ChatMessage(sessionId, MessageRole.ASSISTANT, finalAssistantText, citationsJson);
                assistantMsg = chatMessageRepository.save(assistantMsg);

                ChatMessageResponse assistantDto = new ChatMessageResponse(
                        assistantMsg.getId(),
                        assistantMsg.getRole(),
                        assistantMsg.getContent(),
                        citations,
                        assistantMsg.getCreatedAt()
                );

                emitter.send(SseEmitter.event()
                        .name("assistant_message")
                        .data(objectMapper.writeValueAsString(assistantDto)));

                emitter.send(SseEmitter.event()
                        .name("done")
                        .data("\"[DONE]\""));

                emitter.complete();

            } catch (Exception e) {
                log.error("Error during streaming chat reply: {}", e.getMessage(), e);
                String errorMessage = extractUserFriendlyError(e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(objectMapper.writeValueAsString(java.util.Map.of(
                                    "error", errorMessage))));
                } catch (Exception ignored) {
                }
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {
                }
            }
        });

        return emitter;
    }

    /**
     * Build a rich system prompt that instructs the AI to respond
     * professionally and technically.
     */
    private String buildSystemPrompt(String codeContext) {
        String contextSection = (codeContext == null || codeContext.isBlank())
                ? "No specific code chunks were found for this query. Answer based on your general knowledge."
                : codeContext;

        return """
                You are **DevPilot**, a senior-level AI software engineer and technical consultant.

                ## Core Principles
                1. **Accuracy first** — ground every claim in the code context provided below. \
                   If the context does not contain enough information, state that clearly and offer general best-practice guidance.
                2. **Technical depth** — answer at the level of a senior engineer: explain *why*, \
                   not just *what*. Mention design patterns, trade-offs, complexity, and edge cases when relevant.
                3. **Cite your sources** — when referencing code, always include the file path and line range \
                   (e.g. `src/services/AuthService.java:42-58`).
                4. **Structure for clarity** — use Markdown headings, bullet lists, numbered steps, and \
                   fenced code blocks with language tags (```java, ```typescript, etc.).
                5. **Be concise yet complete** — no filler text. Every sentence should add value.
                6. **Actionable answers** — when suggesting improvements, provide concrete code snippets \
                   ready to copy-paste. Mark changes clearly.

                ## Response Format Guidelines
                - Start with a brief **TL;DR** (1-2 sentences) for long answers.
                - Use `### Heading` sections to organize multi-part answers.
                - Wrap inline code references in backticks: `ClassName.methodName()`.
                - For code suggestions, always include the full import statements needed.
                - When comparing approaches, use a concise table or bullet comparison.

                ## Code Context (from the user's repository)
                %s
                """.formatted(contextSection);
    }

    /**
     * Extract a user-friendly error message from exceptions,
     * especially for common API errors like rate limits or auth failures.
     */
    private String extractUserFriendlyError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";

        if (msg.contains("429") || msg.toLowerCase().contains("rate limit")) {
            return "AI rate limit exceeded. Please wait a moment and try again, or switch to a different provider in Settings.";
        }
        if (msg.contains("401") || msg.contains("403") || msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("invalid api key")) {
            return "AI API key is invalid or expired. Please update your API key in Settings.";
        }
        if (msg.contains("404") || msg.toLowerCase().contains("model not found")) {
            return "The selected AI model was not found. Please check your model name in Settings.";
        }
        if (msg.contains("500") || msg.contains("503")) {
            return "The AI service is temporarily unavailable. Please try again in a few moments.";
        }
        if (msg.contains("timeout")) {
            return "The AI request timed out. Please try a shorter question or try again later.";
        }

        return msg.isBlank() ? "An unexpected error occurred while communicating with the AI service." : msg;
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        List<CitationDto> citations = Collections.emptyList();
        if (message.getCitations() != null && !message.getCitations().isBlank()) {
            try {
                citations = objectMapper.readValue(message.getCitations(), new TypeReference<List<CitationDto>>() {});
            } catch (Exception e) {
                log.warn("Failed to deserialize citations for message {}: {}", message.getId(), e.getMessage());
            }
        }
        return new ChatMessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                citations,
                message.getCreatedAt()
        );
    }
}
