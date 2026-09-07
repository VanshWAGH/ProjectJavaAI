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
    private final VectorStore vectorStore;

    public EmbeddingService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void storeDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        try {
            vectorStore.add(documents);
        } catch (Exception e) {
            log.error("Failed to store documents in vector store", e);
            throw new RuntimeException("Vector store error: " + e.getMessage(), e);
        }
    }

    public List<Document> searchSimilar(UUID repositoryId, String query, int topK) {
        try {
            var filter = new FilterExpressionBuilder().eq("repositoryId", repositoryId.toString()).build();
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .filterExpression(filter)
                    .build();
            return vectorStore.similaritySearch(request);
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
