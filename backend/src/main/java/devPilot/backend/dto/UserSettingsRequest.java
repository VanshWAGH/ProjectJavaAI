package devPilot.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserSettingsRequest(
        @NotBlank(message = "AI provider is required")
        @Pattern(regexp = "^(openai|gemini|anthropic|openrouter)$",
                 message = "Provider must be one of: openai, gemini, anthropic, openrouter")
        String aiProvider,

        @NotBlank(message = "Model name is required")
        String aiModel,

        @NotBlank(message = "API key is required")
        String aiApiKey
) {}
