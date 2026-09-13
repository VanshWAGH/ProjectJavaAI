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
import devPilot.backend.exception.NotFoundException;
import devPilot.backend.repository.ChatMessageRepository;
import devPilot.backend.repository.ChatSessionRepository;
import devPilot.backend.repository.RepositoryRepository;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RepositoryRepository repositoryRepository;
    private final EmbeddingService embeddingService;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public ChatService(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            RepositoryRepository repositoryRepository,
            EmbeddingService embeddingService,
            ChatModel chatModel,
            ObjectMapper objectMapper) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.repositoryRepository = repositoryRepository;
        this.embeddingService = embeddingService;
        this.chatModel = chatModel;
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

                String systemPromptText = """
                        You are DevPilot, an expert AI programming assistant.
                        You have access to the user's codebase context provided below.
                        Answer the user's question accurately and helpfully using the provided code context whenever relevant.
                        Reference filenames and code locations when discussing implementations.
                        If the answer cannot be determined from the code context, say so politely and offer general guidance.

                        Code Context:
                        %s
                        """.formatted(contextBuilder.isEmpty() ? "No specific code chunks found for this query." : contextBuilder.toString());

                List<Message> promptMessages = new ArrayList<>();
                promptMessages.add(new SystemMessage(systemPromptText));

                List<ChatMessage> history = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
                for (ChatMessage m : history) {
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
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(objectMapper.writeValueAsString(java.util.Map.of(
                                    "error", e.getMessage() != null ? e.getMessage() : "Error communicating with AI service"))));
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
