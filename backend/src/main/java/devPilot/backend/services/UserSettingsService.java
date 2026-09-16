package devPilot.backend.services;

import java.util.UUID;

import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import devPilot.backend.dto.UserSettingsRequest;
import devPilot.backend.dto.UserSettingsResponse;
import devPilot.backend.entity.User;
import devPilot.backend.exception.NotFoundException;
import devPilot.backend.repository.UserRepository;

@Service
public class UserSettingsService {

    private final UserRepository userRepository;
    private final AiModelFactory aiModelFactory;
    private final TextEncryptor tokenEncryptor;

    public UserSettingsService(UserRepository userRepository, AiModelFactory aiModelFactory, TextEncryptor tokenEncryptor) {
        this.userRepository = userRepository;
        this.aiModelFactory = aiModelFactory;
        this.tokenEncryptor = tokenEncryptor;
    }

    @Transactional(readOnly = true)
    public UserSettingsResponse getSettings(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        return new UserSettingsResponse(
                user.getAiProvider(),
                user.getAiModel(),
                user.getAiApiKey() != null && !user.getAiApiKey().isBlank()
        );
    }

    @Transactional
    public UserSettingsResponse updateSettings(UUID userId, UserSettingsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        user.setAiProvider(request.aiProvider());
        user.setAiModel(request.aiModel());

        // Only overwrite the API key if a new one was provided (non-blank).
        // This allows users to change provider/model without re-entering their key.
        if (request.aiApiKey() != null && !request.aiApiKey().isBlank()) {
            user.setAiApiKey(tokenEncryptor.encrypt(request.aiApiKey()));
        }

        userRepository.save(user);

        // Invalidate any cached ChatModel so the next chat picks up new settings
        aiModelFactory.evictCache(user);

        return new UserSettingsResponse(
                user.getAiProvider(),
                user.getAiModel(),
                user.getAiApiKey() != null && !user.getAiApiKey().isBlank()
        );
    }
}
