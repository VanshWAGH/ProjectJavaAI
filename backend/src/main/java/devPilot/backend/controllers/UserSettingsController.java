package devPilot.backend.controllers;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import devPilot.backend.dto.UserSettingsRequest;
import devPilot.backend.dto.UserSettingsResponse;
import devPilot.backend.security.CurrentUser;
import devPilot.backend.services.UserSettingsService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/settings")
public class UserSettingsController {

    private final CurrentUser currentUser;
    private final UserSettingsService userSettingsService;

    public UserSettingsController(CurrentUser currentUser, UserSettingsService userSettingsService) {
        this.currentUser = currentUser;
        this.userSettingsService = userSettingsService;
    }

    @GetMapping
    public ResponseEntity<UserSettingsResponse> getSettings() {
        UUID userId = currentUser.require().getId();
        return ResponseEntity.ok(userSettingsService.getSettings(userId));
    }

    @PutMapping
    public ResponseEntity<UserSettingsResponse> updateSettings(
            @Valid @RequestBody UserSettingsRequest request) {
        UUID userId = currentUser.require().getId();
        return ResponseEntity.ok(userSettingsService.updateSettings(userId, request));
    }
}
