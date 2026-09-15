package devPilot.backend.dto;

public record UserSettingsResponse(
        String aiProvider,
        String aiModel,
        boolean hasApiKey
) {}
