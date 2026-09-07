package devPilot.backend.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import devPilot.backend.entity.IndexStatus;
import devPilot.backend.entity.Repository;
import devPilot.backend.entity.User;
import devPilot.backend.repository.RepositoryRepository;
import devPilot.backend.repository.UserRepository;

@Service
public class IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "svg", "ico", "webp",
            "woff", "woff2", "ttf", "eot", "otf",
            "zip", "tar", "gz", "7z", "rar", "jar", "war", "ear",
            "pdf", "exe", "dll", "so", "dylib", "bin", "class", "pyc",
            "mp4", "mp3", "mov", "avi", "wav"
    );

    private static final Set<String> IGNORED_DIRS = Set.of(
            "node_modules", ".git", ".github", ".idea", ".vscode",
            "target", "build", "dist", "out", "vendor", ".next"
    );

    private final RepositoryRepository repositoryRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final GitHubService gitHubService;
    private final EmbeddingService embeddingService;

    @Value("${app.indexing.max-file-bytes:102400}")
    private int maxFileBytes;

    @Value("${app.indexing.chunk-size:800}")
    private int chunkSize;

    @Value("${app.indexing.chunk-overlap:100}")
    private int chunkOverlap;

    @Value("${app.github.api-delay-ms:50}")
    private long apiDelayMs;

    public IndexingService(
            RepositoryRepository repositoryRepository,
            UserRepository userRepository,
            UserService userService,
            GitHubService gitHubService,
            EmbeddingService embeddingService) {
        this.repositoryRepository = repositoryRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.gitHubService = gitHubService;
        this.embeddingService = embeddingService;
    }

    @Async("indexingExecutor")
    public void startIndexing(UUID repositoryId, UUID userId) {
        Repository repo = repositoryRepository.findById(repositoryId).orElse(null);
        if (repo == null) {
            log.error("Cannot index: repository {} not found", repositoryId);
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.error("Cannot index: user {} not found", userId);
            repo.setIndexStatus(IndexStatus.FAILED);
            repo.setErrorMessage("User not found");
            repositoryRepository.save(repo);
            return;
        }

        log.info("Starting indexing for repository: {}", repo.getFullName());
        repo.setIndexStatus(IndexStatus.INDEXING);
        repo.setFilesProcessed(0);
        repo.setFilesTotal(0);
        repo.setChunkCount(0);
        repo.setErrorMessage(null);
        repositoryRepository.save(repo);

        try {
            String token = userService.decryptAccessToken(user);
            List<Map<String, Object>> tree = gitHubService.fetchRepositoryTree(
                    repo.getOwner(), repo.getName(), repo.getDefaultBranch(), token);

            List<Map<String, Object>> filesToIndex = new ArrayList<>();
            for (Map<String, Object> item : tree) {
                if ("blob".equals(item.get("type"))) {
                    String path = String.valueOf(item.get("path"));
                    long size = item.get("size") instanceof Number num ? num.longValue() : 0L;

                    if (shouldIndexFile(path, size)) {
                        filesToIndex.add(item);
                    }
                }
            }

            repo.setFilesTotal(filesToIndex.size());
            repositoryRepository.save(repo);

            embeddingService.deleteForRepository(repositoryId);

            int processedFiles = 0;
            int totalChunks = 0;
            List<Document> batchDocuments = new ArrayList<>();

            for (Map<String, Object> item : filesToIndex) {
                String path = String.valueOf(item.get("path"));
                String content = gitHubService.fetchFileContent(
                        repo.getOwner(), repo.getName(), path, repo.getDefaultBranch(), token);

                if (content != null && !content.isBlank()) {
                    String language = detectLanguage(path);
                    List<Document> chunks = chunkFileContent(repositoryId, path, content, language);
                    batchDocuments.addAll(chunks);
                    totalChunks += chunks.size();

                    if (batchDocuments.size() >= 20) {
                        embeddingService.storeDocuments(batchDocuments);
                        batchDocuments.clear();
                    }
                }

                processedFiles++;
                if (processedFiles % 5 == 0 || processedFiles == filesToIndex.size()) {
                    repo.setFilesProcessed(processedFiles);
                    repo.setChunkCount(totalChunks);
                    repositoryRepository.save(repo);
                }

                if (apiDelayMs > 0) {
                    try {
                        Thread.sleep(apiDelayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            if (!batchDocuments.isEmpty()) {
                embeddingService.storeDocuments(batchDocuments);
                batchDocuments.clear();
            }

            repo.setFilesProcessed(processedFiles);
            repo.setChunkCount(totalChunks);
            repo.setIndexStatus(IndexStatus.READY);
            repo.setIndexedAt(Instant.now());
            repo.setErrorMessage(null);
            repositoryRepository.save(repo);
            log.info("Finished indexing {}: {} files, {} chunks", repo.getFullName(), processedFiles, totalChunks);

        } catch (Exception e) {
            log.error("Indexing failed for {}: {}", repo.getFullName(), e.getMessage(), e);
            repo.setIndexStatus(IndexStatus.FAILED);
            repo.setErrorMessage(e.getMessage() != null ? e.getMessage() : "Unknown indexing failure");
            repositoryRepository.save(repo);
        }
    }

    private boolean shouldIndexFile(String path, long size) {
        if (size > maxFileBytes) {
            return false;
        }

        String[] parts = path.split("/");
        for (String part : parts) {
            if (IGNORED_DIRS.contains(part.toLowerCase())) {
                return false;
            }
        }

        int lastDot = path.lastIndexOf('.');
        if (lastDot != -1) {
            String ext = path.substring(lastDot + 1).toLowerCase();
            if (IGNORED_EXTENSIONS.contains(ext)) {
                return false;
            }
        }

        return true;
    }

    private String detectLanguage(String path) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot == -1) return "Plain Text";
        String ext = path.substring(lastDot + 1).toLowerCase();
        return switch (ext) {
            case "java" -> "Java";
            case "js", "mjs", "cjs" -> "JavaScript";
            case "ts", "mts" -> "TypeScript";
            case "tsx" -> "TypeScript (React)";
            case "jsx" -> "JavaScript (React)";
            case "py" -> "Python";
            case "go" -> "Go";
            case "rs" -> "Rust";
            case "c", "h" -> "C";
            case "cpp", "hpp", "cc" -> "C++";
            case "cs" -> "C#";
            case "rb" -> "Ruby";
            case "php" -> "PHP";
            case "html", "htm" -> "HTML";
            case "css", "scss", "sass", "less" -> "CSS";
            case "json" -> "JSON";
            case "xml" -> "XML";
            case "yaml", "yml" -> "YAML";
            case "md", "markdown" -> "Markdown";
            case "sql" -> "SQL";
            case "sh", "bash", "zsh" -> "Shell";
            default -> ext.toUpperCase();
        };
    }

    private List<Document> chunkFileContent(UUID repoId, String filePath, String content, String language) {
        List<Document> chunks = new ArrayList<>();
        String[] lines = content.split("\r?\n");

        int currentStartLine = 1;
        StringBuilder currentChunk = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            currentChunk.append(line).append("\n");

            if (currentChunk.length() >= chunkSize || i == lines.length - 1) {
                int endLine = i + 1;
                String chunkText = currentChunk.toString();

                Map<String, Object> metadata = Map.of(
                        "repositoryId", repoId.toString(),
                        "filePath", filePath,
                        "startLine", currentStartLine,
                        "endLine", endLine,
                        "language", language
                );

                chunks.add(new Document(chunkText, metadata));

                int rewindLines = Math.max(1, chunkOverlap / 40);
                i = Math.max(currentStartLine - 1, i - rewindLines);
                currentStartLine = i + 2;
                currentChunk.setLength(0);
            }
        }

        return chunks;
    }
}
