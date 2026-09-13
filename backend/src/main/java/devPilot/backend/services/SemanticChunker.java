package devPilot.backend.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * SemanticChunker — Pillar 2: Token Optimization & Semantic Chunking.
 *
 * Splits source-code files at logical boundaries instead of arbitrary character counts:
 *  - Java / Kotlin  → class, interface, method blocks (brace-depth tracking)
 *  - JavaScript / TypeScript → function, const arrow, export boundaries
 *  - Python         → def / class indentation resets
 *  - All other      → sliding character-window fallback (same as before)
 *
 * Each chunk is guaranteed to fit within the embedding model's 512-token limit
 * because MAX_LINE_CHARS (200) was already enforced by IndexingService upstream.
 */
@Service
public class SemanticChunker {

    private static final Logger log = LoggerFactory.getLogger(SemanticChunker.class);

    @Value("${app.indexing.chunk-size:300}")
    private int chunkSize;

    @Value("${app.indexing.chunk-overlap:50}")
    private int chunkOverlap;

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Chunk {@code content} semantically based on its {@code language}.
     *
     * @param repoId     repository UUID (written into metadata)
     * @param filePath   relative file path (written into metadata)
     * @param content    raw (already governance-sanitized) file content
     * @param language   detected language string (from IndexingService.detectLanguage)
     * @param extraMeta  additional metadata key-value pairs to attach to every chunk
     * @return list of Spring AI {@link Document} chunks with full metadata
     */
    public List<Document> chunk(UUID repoId, String filePath, String content,
                                 String language, Map<String, Object> extraMeta) {
        if (content == null || content.isBlank()) return List.of();

        List<Document> chunks = switch (language) {
            case "Java", "Kotlin", "C#", "C", "C++", "Go", "Rust" ->
                    chunkByBraceBlocks(content);
            case "JavaScript", "JavaScript (React)", "TypeScript", "TypeScript (React)" ->
                    chunkByJsFunctions(content);
            case "Python" ->
                    chunkByPythonDefs(content);
            default ->
                    chunkBySlidingWindow(content);
        };

        // Attach full metadata to every chunk
        List<Document> enriched = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Document raw = chunks.get(i);
            Map<String, Object> meta = new java.util.HashMap<>(raw.getMetadata());
            meta.put("repositoryId", repoId.toString());
            meta.put("filePath",     filePath);
            meta.put("language",     language);
            meta.put("chunkIndex",   i);
            meta.putAll(extraMeta);
            enriched.add(new Document(raw.getText(), meta));
        }

        return enriched;
    }

    // ── Java / C-family: brace-depth chunking ──────────────────────────────────

    /**
     * Splits by top-level brace blocks ({@code {…}}).
     * Each block that opens at depth 0→1 (class, method, struct, fn) becomes a chunk.
     * Consecutive small blocks are merged until they hit {@code chunkSize}.
     */
    private List<Document> chunkByBraceBlocks(String content) {
        List<Document> chunks = new ArrayList<>();
        String[] lines = content.split("\r?\n", -1);

        int depth = 0;
        int blockStart = 0;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            current.append(line).append("\n");

            for (char c : line.toCharArray()) {
                if (c == '{') depth++;
                else if (c == '}') depth--;
            }

            // A top-level block just closed, or we've hit chunk size
            boolean blockClosed = (depth == 0 && current.length() > 0);
            boolean sizeHit = current.length() >= chunkSize;

            if ((blockClosed || sizeHit) && current.length() > 0) {
                chunks.add(rawChunk(current.toString(), blockStart + 1, i + 1));
                blockStart = i + 1;
                current.setLength(0);
            }
        }

        if (current.length() > 0) {
            chunks.add(rawChunk(current.toString(), blockStart + 1, lines.length));
        }

        return chunks.isEmpty() ? chunkBySlidingWindow(content) : chunks;
    }

    // ── JavaScript / TypeScript: function boundary chunking ────────────────────

    private static final java.util.regex.Pattern JS_BOUNDARY = java.util.regex.Pattern.compile(
            "^\\s*(export\\s+)?(default\\s+)?(async\\s+)?" +
            "(function\\b|class\\b|const\\s+\\w+\\s*=|let\\s+\\w+\\s*=|var\\s+\\w+\\s*=|" +
            "describe\\(|it\\(|test\\(|@\\w+)");

    private List<Document> chunkByJsFunctions(String content) {
        return chunkByLineBoundary(content, JS_BOUNDARY);
    }

    // ── Python: def/class indent-reset chunking ─────────────────────────────────

    private static final java.util.regex.Pattern PY_BOUNDARY =
            java.util.regex.Pattern.compile("^(def |class |async def )");

    private List<Document> chunkByPythonDefs(String content) {
        return chunkByLineBoundary(content, PY_BOUNDARY);
    }

    // ── Generic boundary-line chunking ──────────────────────────────────────────

    /**
     * Splits content at lines matching {@code boundaryPattern}.
     * When a boundary line is found and the accumulated chunk is non-trivial,
     * it is flushed. An overlap window from the previous chunk is prepended.
     */
    private List<Document> chunkByLineBoundary(String content,
                                                java.util.regex.Pattern boundaryPattern) {
        List<Document> chunks = new ArrayList<>();
        String[] lines = content.split("\r?\n", -1);

        int startLine = 0;
        StringBuilder current = new StringBuilder();
        String overlapBuffer = "";

        for (int i = 0; i < lines.length; i++) {
            boolean isBoundary = boundaryPattern.matcher(lines[i]).find();

            if (isBoundary && current.length() >= chunkSize / 2 && current.length() > 0) {
                chunks.add(rawChunk(overlapBuffer + current, startLine + 1, i));
                // Carry last `chunkOverlap` chars as overlap into next chunk
                String full = current.toString();
                overlapBuffer = full.length() > chunkOverlap
                        ? full.substring(full.length() - chunkOverlap)
                        : full;
                startLine = i;
                current.setLength(0);
            }

            current.append(lines[i]).append("\n");

            // Hard size cap — flush even mid-function for very large functions
            if (current.length() >= chunkSize * 3) {
                chunks.add(rawChunk(overlapBuffer + current, startLine + 1, i + 1));
                String full = current.toString();
                overlapBuffer = full.length() > chunkOverlap
                        ? full.substring(full.length() - chunkOverlap)
                        : full;
                startLine = i + 1;
                current.setLength(0);
            }
        }

        if (current.length() > 0) {
            chunks.add(rawChunk(overlapBuffer + current, startLine + 1, lines.length));
        }

        return chunks.isEmpty() ? chunkBySlidingWindow(content) : chunks;
    }

    // ── Sliding-window fallback ──────────────────────────────────────────────────

    /**
     * Original character-count sliding window — used for languages without
     * semantic boundary detection (SQL, YAML, Markdown, Shell, etc.).
     */
    private List<Document> chunkBySlidingWindow(String content) {
        List<Document> chunks = new ArrayList<>();
        String[] lines = content.split("\r?\n", -1);

        int currentStartLine = 1;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            current.append(lines[i]).append("\n");

            if (current.length() >= chunkSize || i == lines.length - 1) {
                chunks.add(rawChunk(current.toString(), currentStartLine, i + 1));

                if (i == lines.length - 1) break;

                int rewindLines = Math.max(1, chunkOverlap / 40);
                i = Math.max(currentStartLine - 1, i - rewindLines);
                currentStartLine = i + 2;
                current.setLength(0);
            }
        }
        return chunks;
    }

    // ── Helper ───────────────────────────────────────────────────────────────────

    /** Creates a Document with only line-range metadata; caller adds the rest. */
    private static Document rawChunk(Object text, int startLine, int endLine) {
        return new Document(text.toString(),
                Map.of("startLine", startLine, "endLine", endLine));
    }
}
