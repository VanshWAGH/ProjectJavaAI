package devPilot.backend.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import devPilot.backend.dto.IndexStatusResponse;
import devPilot.backend.dto.RepositoryResponse;
import devPilot.backend.security.CurrentUser;
import devPilot.backend.services.RepoService;

@RestController
@RequestMapping("/api/repos")
public class RepoController {

    private final CurrentUser currentUser;
    private final RepoService repoService;

    public RepoController(CurrentUser currentUser, RepoService repoService) {
        this.currentUser = currentUser;
        this.repoService = repoService;
    }

    @GetMapping
    public List<RepositoryResponse> listRepos(
            @RequestParam(defaultValue = "true") boolean refresh) {
        UUID userId = currentUser.require().getId();
        return repoService.listRepositories(userId, refresh);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RepositoryResponse> getRepo(@PathVariable UUID id) {
        UUID userId = currentUser.require().getId();
        return ResponseEntity.ok(repoService.getRepository(userId, id));
    }

    @PostMapping("/{id}/index")
    public ResponseEntity<RepositoryResponse> startIndex(@PathVariable UUID id) {
        UUID userId = currentUser.require().getId();
        return ResponseEntity.ok(repoService.triggerIndexing(userId, id));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<IndexStatusResponse> getStatus(@PathVariable UUID id) {
        UUID userId = currentUser.require().getId();
        return ResponseEntity.ok(repoService.getIndexStatus(userId, id));
    }
}