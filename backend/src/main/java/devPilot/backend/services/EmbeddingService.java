package devPilot.backend.services;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    /** Maximum number of retry attempts for transient embedding API errors (e.g. 404, 503). */
    private static final int MAX_RETRIES = 3;

    /** Base back-off delay in milliseconds; doubles each retry: 1 s → 2 s → 4 s. */
    private static final long BACKOFF_BASE_MS = 1_000;

    private final VectorStore vectorStore;

    public EmbeddingService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Store a batch of documents in the vector store with retry-with-backoff.
     * Falls back to per-document submission if the batch call fails, so that a
     * single bad document never blocks the rest of the batch.
     */
    public void storeDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        Exception lastException = null;

        // ── 1. Try the whole batch with retries ─────────────────────────────
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                vectorStore.add(documents);
                return; // success
            } catch (Exception e) {
                lastException = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";

                // Only retry on transient errors (404 model-not-found, 503 overload, rate-limits)
                boolean isTransient = msg.contains("404") || msg.contains("503")
                        || msg.contains("429") || msg.contains("timeout");

                if (!isTransient || attempt == MAX_RETRIES) {
                    break; // fall through to per-document fallback
                }

                long delay = BACKOFF_BASE_MS * (1L << (attempt - 1)); // 1 s, 2 s, 4 s
                log.warn("Batch embed attempt {}/{} failed ({}). Retrying in {} ms…",
                        attempt, MAX_RETRIES, msg, delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during embedding retry", ie);
                }
            }
        }

        // ── 2. Fall back to per-document submission ──────────────────────────
        log.warn("Batch vector store add failed ({}), falling back to per-document mode…",
                lastException != null ? lastException.getMessage() : "unknown");

        int successCount = 0;
        for (Document doc : documents) {
            boolean stored = storeDocumentWithRetry(doc);
            if (stored) successCount++;
        }

        if (successCount == 0) {
            throw new RuntimeException(
                    "Vector store error: all documents in batch failed. Last error: "
                    + (lastException != null ? lastException.getMessage() : "unknown"),
                    lastException);
        }
        log.info("Per-document fallback stored {}/{} documents", successCount, documents.size());
    }

    /** Attempt to store a single document, retrying on transient errors. */
    private boolean storeDocumentWithRetry(Document doc) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                vectorStore.add(List.of(doc));
                return true;
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                boolean isTransient = msg.contains("404") || msg.contains("503")
                        || msg.contains("429") || msg.contains("timeout");

                if (!isTransient || attempt == MAX_RETRIES) {
                    log.warn("Skipping document in file {} after {} attempt(s): {}",
                            doc.getMetadata().get("filePath"), attempt, msg);
                    return false;
                }

                long delay = BACKOFF_BASE_MS * (1L << (attempt - 1));
                log.warn("Doc embed attempt {}/{} failed ({}). Retrying in {} ms…",
                        attempt, MAX_RETRIES, msg, delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    public List<Document> searchSimilar(UUID repositoryId, String query, int topK) {
        long start = System.currentTimeMillis();
        try {
            var filter = new FilterExpressionBuilder().eq("repositoryId", repositoryId.toString()).build();
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .filterExpression(filter)
                    .build();
            List<Document> results = vectorStore.similaritySearch(request);
            long latencyMs = System.currentTimeMillis() - start;

            // ── Retrieval Telemetry (Pillar 5: Pipeline Evaluation) ────────────
            // Hit-rate proxy: did we get any results at all?
            double hitRate = results.isEmpty() ? 0.0 : 1.0;
            log.info("🔍 Retrieval: repo={} topK={} returned={} hitRate={} latency={}ms query=\"{}\"",
                    repositoryId, topK, results.size(), hitRate, latencyMs,
                    query.length() > 60 ? query.substring(0, 60) + "…" : query);

            return results;
        } catch (Exception e) {
            log.warn("Vector search failed for repo {}: {}", repositoryId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public void deleteForRepository(UUID repositoryId) {
        try {
            var filter = new FilterExpressionBuilder().eq("repositoryId", repositoryId.toString()).build();
            vectorStore.delete(filter);
        } catch (Exception e) {
            log.warn("Could not delete previous vector embeddings for repo {}: {}", repositoryId, e.getMessage());
        }
    }
}
