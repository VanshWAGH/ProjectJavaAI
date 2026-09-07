package devPilot.backend.services;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GitHubService {

    private static final Logger log = LoggerFactory.getLogger(GitHubService.class);
    private static final String GITHUB_API_URL = "https://api.github.com";

    private final RestClient restClient;

    public GitHubService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl(GITHUB_API_URL)
                .defaultHeader(HttpHeaders.USER_AGENT, "DevPilot-App")
                .build();
    }

    public List<Map<String, Object>> fetchUserRepositories(String decryptedToken) {
        try {
            List<Map<String, Object>> repos = restClient.get()
                    .uri("/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator,organization_member")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + decryptedToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            return repos != null ? repos : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch repositories from GitHub", e);
            throw new RuntimeException("Could not fetch repositories from GitHub: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> fetchRepositoryTree(String owner, String repo, String branch, String decryptedToken) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/repos/{owner}/{repo}/git/trees/{branch}?recursive=1", owner, repo, branch)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + decryptedToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response != null && response.get("tree") instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tree = (List<Map<String, Object>>) list;
                return tree;
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch git tree for {}/{} branch {}", owner, repo, branch, e);
            throw new RuntimeException("Failed to fetch file tree for repository: " + e.getMessage(), e);
        }
    }

    public String fetchFileContent(String owner, String repo, String path, String branch, String decryptedToken) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/contents/{path}")
                            .queryParam("ref", branch)
                            .build(owner, repo, path))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + decryptedToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github.raw")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        log.warn("Failed to fetch file {} in {}/{}: status {}", path, owner, repo, res.getStatusCode());
                    })
                    .body(String.class);
        } catch (Exception e) {
            log.debug("Error retrieving file content for {}: {}", path, e.getMessage());
            return null;
        }
    }
}
