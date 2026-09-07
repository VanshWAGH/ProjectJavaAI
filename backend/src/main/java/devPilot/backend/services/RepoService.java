package devPilot.backend.services;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import devPilot.backend.dto.IndexStatusResponse;
import devPilot.backend.dto.RepositoryResponse;
import devPilot.backend.entity.IndexStatus;
import devPilot.backend.entity.Repository;
import devPilot.backend.entity.User;
import devPilot.backend.exception.NotFoundException;
import devPilot.backend.repository.RepositoryRepository;

@Service
public class RepoService {

    private static final Logger log = LoggerFactory.getLogger(RepoService.class);

    private final RepositoryRepository repositoryRepository;
    private final UserService userService;
    private final GitHubService gitHubService;
    private final IndexingService indexingService;

    public RepoService(
            RepositoryRepository repositoryRepository,
            UserService userService,
            GitHubService gitHubService,
            IndexingService indexingService) {
        this.repositoryRepository = repositoryRepository;
        this.userService = userService;
        this.gitHubService = gitHubService;
        this.indexingService = indexingService;
    }

    @Transactional
    public List<RepositoryResponse> listRepositories(UUID userId, boolean refresh) {
        if (refresh) {
            syncFromGitHub(userId);
        }

        return repositoryRepository.findByUserIdOrderByFullNameAsc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RepositoryResponse getRepository(UUID userId, UUID repoId) {
        Repository repo = repositoryRepository.findByIdAndUserId(repoId, userId)
                .orElseThrow(() -> new NotFoundException("Repository not found"));
        return toResponse(repo);
    }

    @Transactional
    public RepositoryResponse triggerIndexing(UUID userId, UUID repoId) {
        Repository repo = repositoryRepository.findByIdAndUserId(repoId, userId)
                .orElseThrow(() -> new NotFoundException("Repository not found"));

        indexingService.startIndexing(repo.getId(), userId);
        return toResponse(repo);
    }

    @Transactional(readOnly = true)
    public IndexStatusResponse getIndexStatus(UUID userId, UUID repoId) {
        Repository repo = repositoryRepository.findByIdAndUserId(repoId, userId)
                .orElseThrow(() -> new NotFoundException("Repository not found"));

        return new IndexStatusResponse(
                repo.getId(),
                repo.getIndexStatus(),
                repo.getFilesTotal(),
                repo.getFilesProcessed(),
                repo.getChunkCount(),
                repo.getIndexedAt(),
                repo.getErrorMessage()
        );
    }

    private void syncFromGitHub(UUID userId) {
        try {
            User user = userService.requiredById(userId);
            String token = userService.decryptAccessToken(user);
            List<Map<String, Object>> ghRepos = gitHubService.fetchUserRepositories(token);

            for (Map<String, Object> map : ghRepos) {
                Long githubRepoId = ((Number) map.get("id")).longValue();
                Repository repo = repositoryRepository.findByUserIdAndGithubRepoId(userId, githubRepoId)
                        .orElseGet(Repository::new);

                repo.setUserId(userId);
                repo.setGithubRepoId(githubRepoId);
                repo.setName(String.valueOf(map.get("name")));
                repo.setFullName(String.valueOf(map.get("full_name")));

                if (map.get("owner") instanceof Map<?, ?> ownerMap) {
                    repo.setOwner(String.valueOf(ownerMap.get("login")));
                } else {
                    repo.setOwner(user.getGithubUsername());
                }

                repo.setPrivate(Boolean.TRUE.equals(map.get("private")));
                repo.setDefaultBranch(map.get("default_branch") != null ? String.valueOf(map.get("default_branch")) : "main");
                repo.setLanguage(map.get("language") != null ? String.valueOf(map.get("language")) : null);
                repo.setHtmlUrl(map.get("html_url") != null ? String.valueOf(map.get("html_url")) : null);
                repo.setDescription(map.get("description") != null ? String.valueOf(map.get("description")) : null);

                if (repo.getIndexStatus() == null) {
                    repo.setIndexStatus(IndexStatus.PENDING);
                }

                repositoryRepository.save(repo);
            }
        } catch (Exception e) {
            log.error("Failed syncing repos from GitHub for user {}: {}", userId, e.getMessage(), e);
        }
    }

    private RepositoryResponse toResponse(Repository r) {
        return new RepositoryResponse(
                r.getId(),
                r.getGithubRepoId(),
                r.getOwner(),
                r.getName(),
                r.getFullName(),
                r.isPrivate(),
                r.getDefaultBranch(),
                r.getLanguage(),
                r.getHtmlUrl(),
                r.getDescription(),
                r.getIndexStatus(),
                r.getIndexedAt(),
                r.getChunkCount(),
                r.getFilesTotal(),
                r.getFilesProcessed(),
                r.getErrorMessage()
        );
    }
}
