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

/**
 * IndexingService — orchestrates the full RAG ingestion pipeline.
 *
 * Pipeline stages (in order):
 *  1. Fetch repository file tree from GitHub
 *  2. Filter ignorable / generated files          [ContentGovernanceService]
 *  3. Fetch file content from GitHub
 *  4. Strip license headers                       [ContentGovernanceService]
 *  5. Redact secrets / PII                        [ContentGovernanceService]
 *  6. Semantic chunking                           [SemanticChunker]
 *  7. Exact deduplication                         [ChunkDeduplicator]
 *  8. Batch embedding + storage                   [EmbeddingService]
 */
@Service
public class IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

    // ── Files to skip regardless of size ──────────────────────────────────────

    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            // Binary media
            "png", "jpg", "jpeg", "gif", "svg", "ico", "webp",
            // Fonts
            "woff", "woff2", "ttf", "eot", "otf",
            // Archives & compiled
            "zip", "tar", "gz", "7z", "rar", "jar", "war", "ear",
            "pdf", "exe", "dll", "so", "dylib", "bin", "class", "pyc",
            // Audio / Video
            "mp4", "mp3", "mov", "avi", "wav",
            // Source maps & minified (also caught by ContentGovernanceService)
            "map",
            // Lock files & snapshots
            "lock", "snap",
            // Data files
            "json", "xml", "yaml", "yml", "csv", "sql"
    );

    private static final Set<String> IGNORED_DIRS = Set.of(
            "node_modules", ".git", ".github", ".idea", ".vscode",
            "target", "build", "dist", "out", "vendor", ".next",
            "__pycache__", ".pytest_cache", "coverage", ".nyc_output"
    );

    // ── Embedding batch size (free-tier safe) ──────────────────────────────────
    /** Small batches protect against free-tier rate limits and make retries cheaper. */
    private static final int EMBED_BATCH_SIZE = 5;

    // ── Dependencies ───────────────────────────────────────────────────────────

    private final RepositoryRepository    repositoryRepository;
    private final UserRepository          userRepository;
    private final UserService             userService;
    private final GitHubService           gitHubService;
    private final EmbeddingService        embeddingService;
    private final ContentGovernanceService governance;
    private final SemanticChunker         semanticChunker;
    private final ChunkDeduplicator       chunkDeduplicator;

    @Value("${app.indexing.max-file-bytes:102400}")
    private int maxFileBytes;

    @Value("${app.github.api-delay-ms:50}")
    private long apiDelayMs;

    public IndexingService(
            RepositoryRepository    repositoryRepository,
            UserRepository          userRepository,
            UserService             userService,
            GitHubService           gitHubService,
            EmbeddingService        embeddingService,
            ContentGovernanceService governance,
            SemanticChunker         semanticChunker,
            ChunkDeduplicator       chunkDeduplicator) {
        this.repositoryRepository = repositoryRepository;
        this.userRepository       = userRepository;
        this.userService          = userService;
        this.gitHubService        = gitHubService;
        this.embeddingService     = embeddingService;
        this.governance           = governance;
        this.semanticChunker      = semanticChunker;
        this.chunkDeduplicator    = chunkDeduplicator;
    }

    // ── Main entry point ───────────────────────────────────────────────────────

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

        log.info("▶ Indexing started: {}", repo.getFullName());
        long startMs = System.currentTimeMillis();

        repo.setIndexStatus(IndexStatus.INDEXING);
        repo.setFilesProcessed(0);
        repo.setFilesTotal(0);
        repo.setChunkCount(0);
        repo.setErrorMessage(null);
        repositoryRepository.save(repo);

        try {
            // ── Stage 1: Fetch file tree ────────────────────────────────────
            String token = userService.decryptAccessToken(user);
            List<Map<String, Object>> tree = gitHubService.fetchRepositoryTree(
                    repo.getOwner(), repo.getName(), repo.getDefaultBranch(), token);

            // ── Stage 2: Filter blobs ──────────────────────────────────────
            List<Map<String, Object>> filesToIndex = new ArrayList<>();
            for (Map<String, Object> item : tree) {
                if ("blob".equals(item.get("type"))) {
                    String path = String.valueOf(item.get("path"));
                    long size   = item.get("size") instanceof Number n ? n.longValue() : 0L;
                    if (shouldIndexFile(path, size)) filesToIndex.add(item);
                }
            }

            repo.setFilesTotal(filesToIndex.size());
            repositoryRepository.save(repo);

            // ── Stage 3: Clear old vectors ─────────────────────────────────
            embeddingService.deleteForRepository(repositoryId);

            // ── Per-run deduplication context ─────────────────────────────
            ChunkDeduplicator.RunContext dedup = chunkDeduplicator.newRun();

            // ── Lineage metadata shared across all chunks in this run ─────
            String repoVisibility = repo.isPrivate() ? "private" : "public";
            String indexedAt      = Instant.now().toString();

            // ── Counters ──────────────────────────────────────────────────
            int processedFiles   = 0;
            int skippedGenerated = 0;
            int totalChunks      = 0;

            List<Document> batch = new ArrayList<>();

            for (Map<String, Object> item : filesToIndex) {
                String path = String.valueOf(item.get("path"));

                // ── Stage 4: Fetch content ────────────────────────────────
                String rawContent = gitHubService.fetchFileContent(
                        repo.getOwner(), repo.getName(), path,
                        repo.getDefaultBranch(), token);

                if (rawContent == null || rawContent.isBlank()) {
                    processedFiles++;
                    continue;
                }

                // ── Stage 5: Governance — generated-file gate ─────────────
                if (governance.isGeneratedFile(path, rawContent)) {
                    skippedGenerated++;
                    processedFiles++;
                    continue;
                }

                // ── Stage 6: Governance — strip license header ────────────
                String cleaned = governance.stripLicenseHeader(rawContent);

                // ── Stage 7: Governance — redact secrets / PII ────────────
                cleaned = governance.redactSensitiveData(cleaned, path);

                // ── Stage 8: Semantic chunking ────────────────────────────
                String language = detectLanguage(path);
                String fileName = path.contains("/")
                        ? path.substring(path.lastIndexOf('/') + 1)
                        : path;

                Map<String, Object> extraMeta = Map.of(
                        "fileName",       fileName,
                        "repoOwner",      repo.getOwner(),
                        "repoName",       repo.getName(),
                        "repoVisibility", repoVisibility,
                        "indexedAt",      indexedAt
                );

                List<Document> chunks = semanticChunker.chunk(
                        repositoryId, path, cleaned, language, extraMeta);

                // ── Stage 9: Deduplication ────────────────────────────────
                List<Document> uniqueChunks = dedup.deduplicate(chunks);
                batch.addAll(uniqueChunks);
                totalChunks += uniqueChunks.size();

                // ── Stage 10: Batch flush ─────────────────────────────────
                if (batch.size() >= EMBED_BATCH_SIZE) {
                    embeddingService.storeDocuments(batch);
                    batch.clear();
                }

                processedFiles++;
                if (processedFiles % 5 == 0 || processedFiles == filesToIndex.size()) {
                    repo.setFilesProcessed(processedFiles);
                    repo.setChunkCount(totalChunks);
                    repositoryRepository.save(repo);
                }

                if (apiDelayMs > 0) {
                    try { Thread.sleep(apiDelayMs); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
            }

            // ── Flush remainder ───────────────────────────────────────────
            if (!batch.isEmpty()) {
                embeddingService.storeDocuments(batch);
            }

            // ── Finalise ──────────────────────────────────────────────────
            long elapsed = System.currentTimeMillis() - startMs;
            repo.setFilesProcessed(processedFiles);
            repo.setChunkCount(totalChunks);
            repo.setIndexStatus(IndexStatus.READY);
            repo.setIndexedAt(Instant.now());
            repo.setErrorMessage(null);
            repositoryRepository.save(repo);

            log.info("✅ Indexing complete: {} | files: {}/{} indexed ({} generated/skipped) | " +
                     "chunks: {} stored | {} | {}ms",
                    repo.getFullName(),
                    processedFiles - skippedGenerated, filesToIndex.size(),
                    skippedGenerated,
                    totalChunks,
                    dedup.summary(),
                    elapsed);

        } catch (Exception e) {
            log.error("❌ Indexing failed for {}: {}", repo.getFullName(), e.getMessage(), e);
            repo.setIndexStatus(IndexStatus.FAILED);
            repo.setErrorMessage(e.getMessage() != null ? e.getMessage() : "Unknown indexing failure");
            repositoryRepository.save(repo);
        }
    }

    // ── File filtering ─────────────────────────────────────────────────────────

    private boolean shouldIndexFile(String path, long size) {
        if (size > maxFileBytes) return false;

        String[] parts = path.split("/");
        for (String part : parts) {
            if (IGNORED_DIRS.contains(part.toLowerCase())) return false;
        }

        int lastDot = path.lastIndexOf('.');
        if (lastDot != -1) {
            String ext = path.substring(lastDot + 1).toLowerCase();
            if (IGNORED_EXTENSIONS.contains(ext)) return false;
        }

        return true;
    }

    // ── Language detection ─────────────────────────────────────────────────────

    private String detectLanguage(String path) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot == -1) return "Plain Text";
        String ext = path.substring(lastDot + 1).toLowerCase();
        return switch (ext) {
            case "java"                -> "Java";
            case "kt", "kts"          -> "Kotlin";
            case "js", "mjs", "cjs"   -> "JavaScript";
            case "ts", "mts"          -> "TypeScript";
            case "tsx"                -> "TypeScript (React)";
            case "jsx"                -> "JavaScript (React)";
            case "py"                 -> "Python";
            case "go"                 -> "Go";
            case "rs"                 -> "Rust";
            case "c", "h"             -> "C";
            case "cpp", "hpp", "cc"   -> "C++";
            case "cs"                 -> "C#";
            case "rb"                 -> "Ruby";
            case "php"                -> "PHP";
            case "html", "htm"        -> "HTML";
            case "css", "scss", "sass", "less" -> "CSS";
            case "json"               -> "JSON";
            case "xml"                -> "XML";
            case "yaml", "yml"        -> "YAML";
            case "md", "markdown"     -> "Markdown";
            case "sql"                -> "SQL";
            case "sh", "bash", "zsh"  -> "Shell";
            default                   -> ext.toUpperCase();
        };
    }
}
