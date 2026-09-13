package devPilot.backend.dto;

public record CitationDto(
        String filePath,
        String fileName,
        Integer startLine,
        Integer endLine,
        String language,
        String repoOwner,
        String indexedAt) {
}