package devPilot.backend.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

/**
 * ChunkDeduplicator — Pillar 4: Data Hygiene & Exact Deduplication.
 *
 * Computes MD5(chunkText) for every proposed chunk and filters out exact
 * duplicates within a single indexing run.  Common culprits:
 *  - Copy-pasted boilerplate / utility helpers across multiple files
 *  - Repeated license blocks that survived the governance stripping step
 *  - Auto-generated getter/setter patterns in Java POJOs
 *
 * Usage per indexing run:
 *   ChunkDeduplicator dedup = chunkDeduplicator.newRun();
 *   List&lt;Document&gt; unique = dedup.deduplicate(candidates);
 *
 * The returned documents have an additional {@code chunkHash} metadata field
 * so that future cross-run semantic-dedup extensions can compare hashes stored
 * in pgvector's metadata JSONB column.
 */
@Service
public class ChunkDeduplicator {

    private static final Logger log = LoggerFactory.getLogger(ChunkDeduplicator.class);

    /**
     * Returns a new per-run deduplication context. Call this once per
     * {@code startIndexing()} invocation so the seen-set resets between runs.
     */
    public RunContext newRun() {
        return new RunContext();
    }

    /** Stateful context for one indexing run. Not thread-safe by design
     *  (each async indexing task uses its own instance). */
    public static class RunContext {

        private final Set<String> seenHashes = new HashSet<>();
        private int totalSeen   = 0;
        private int totalSkipped = 0;

        /**
         * Filters {@code candidates} to only those whose content has not been
         * seen in this run. Attaches {@code chunkHash} to each surviving document's
         * metadata.
         *
         * @return new list containing only unique documents (order preserved)
         */
        public List<Document> deduplicate(List<Document> candidates) {
            return candidates.stream()
                    .filter(doc -> {
                        totalSeen++;
                        String hash = md5(doc.getText());
                        if (seenHashes.contains(hash)) {
                            totalSkipped++;
                            log.debug("Dedup: skipping duplicate chunk in {}",
                                    doc.getMetadata().getOrDefault("filePath", "?"));
                            return false;
                        }
                        seenHashes.add(hash);

                        // Attach hash to metadata for lineage / future cross-run dedup
                        doc.getMetadata().put("chunkHash", hash);
                        return true;
                    })
                    .toList();
        }

        /** Number of chunks examined so far in this run. */
        public int getTotalSeen()    { return totalSeen; }

        /** Number of exact-duplicate chunks skipped in this run. */
        public int getTotalSkipped() { return totalSkipped; }

        /** Returns a summary string for logging at run completion. */
        public String summary() {
            return String.format("dedup: %d seen, %d unique, %d duplicates skipped (%.1f%%)",
                    totalSeen,
                    totalSeen - totalSkipped,
                    totalSkipped,
                    totalSeen > 0 ? (100.0 * totalSkipped / totalSeen) : 0.0);
        }

        // ── MD5 helper ────────────────────────────────────────────────────────

        private static String md5(String text) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder(32);
                for (byte b : hash) hex.append(String.format("%02x", b));
                return hex.toString();
            } catch (NoSuchAlgorithmException e) {
                // MD5 is guaranteed present in all JVMs; this branch is unreachable.
                throw new IllegalStateException("MD5 unavailable", e);
            }
        }
    }
}
