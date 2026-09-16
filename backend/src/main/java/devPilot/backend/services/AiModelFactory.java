package devPilot.backend.services;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

import devPilot.backend.entity.User;

/**
 * Dynamically creates per-user ChatModel instances based on their stored
 * AI provider preferences (BYOK — Bring Your Own Key).
 *
 * Supported providers:
 *   • openai      → direct OpenAI API
 *   • gemini      → Google Gemini via its OpenAI-compatible endpoint
 *   • anthropic   → Anthropic Claude via OpenRouter gateway (OpenAI-compatible)
 *   • openrouter  → any model routed through OpenRouter
 */
@Component
public class AiModelFactory {

    private static final Logger log = LoggerFactory.getLogger(AiModelFactory.class);

    /**
     * Provider → base URL mapping.
     * Gemini exposes an OpenAI-compatible REST API at the URL below,
     * so we can reuse the same OpenAiChatModel class for all four providers.
     */
    private static final Map<String, String> PROVIDER_BASE_URLS = Map.of(
            "openai",     "https://api.openai.com",
            "gemini",     "https://generativelanguage.googleapis.com/v1beta/openai",
            "anthropic",  "https://openrouter.ai/api/v1",
            "openrouter", "https://openrouter.ai/api/v1"
    );

    /** Default models per provider when the user doesn't specify one. */
    private static final Map<String, String> DEFAULT_MODELS = Map.of(
            "openai",     "gpt-4o-mini",
            "gemini",     "gemini-2.0-flash",
            "anthropic",  "anthropic/claude-sonnet-4",
            "openrouter", "openrouter/auto"
    );

    /** Simple LRU-ish cache keyed by "userId:provider:model:keyHash". */
    private final ConcurrentHashMap<String, ChatModel> modelCache = new ConcurrentHashMap<>();

    private final TextEncryptor tokenEncryptor;

    public AiModelFactory(TextEncryptor tokenEncryptor) {
        this.tokenEncryptor = tokenEncryptor;
    }

    /**
     * Build (or retrieve from cache) a ChatModel configured with the user's
     * credentials.  Falls back to the singleton Spring-managed ChatModel if
     * the user has no BYOK settings.
     */
    public ChatModel getModelForUser(User user, ChatModel defaultModel) {
        if (user.getAiProvider() == null || user.getAiApiKey() == null) {
            return defaultModel;
        }

        String provider = user.getAiProvider().toLowerCase();
        String model = user.getAiModel() != null && !user.getAiModel().isBlank()
                ? user.getAiModel()
                : DEFAULT_MODELS.getOrDefault(provider, "gpt-4o-mini");
        
        // Decrypt the API key
        String decryptedKey;
        try {
            decryptedKey = tokenEncryptor.decrypt(user.getAiApiKey());
        } catch (Exception e) {
            log.warn("Failed to decrypt AI API key for user {}: {}", user.getId(), e.getMessage());
            decryptedKey = user.getAiApiKey(); // Fallback if plain text (e.g. legacy/testing)
        }
        final String apiKey = decryptedKey;

        String cacheKey = user.getId() + ":" + provider + ":" + model + ":" + apiKey.hashCode();

        return modelCache.computeIfAbsent(cacheKey, k -> {
            log.info("Creating ChatModel for user {} with provider={} model={}", user.getId(), provider, model);
            return createChatModel(provider, apiKey, model);
        });
    }

    /**
     * Invalidate the cached model for a user (call after settings change).
     */
    public void evictCache(User user) {
        modelCache.entrySet().removeIf(e -> e.getKey().startsWith(user.getId().toString()));
    }

    private ChatModel createChatModel(String provider, String apiKey, String model) {
        String baseUrl = PROVIDER_BASE_URLS.getOrDefault(provider, PROVIDER_BASE_URLS.get("openrouter"));

        OpenAIClient openAiClient = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .maxTokens(4096)
                .temperature(0.3)
                .build();

        return OpenAiChatModel.builder()
                .openAiClient(openAiClient)
                .options(options)
                .build();
    }
}
