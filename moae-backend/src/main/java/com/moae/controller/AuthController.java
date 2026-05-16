package com.moae.controller;

import com.moae.dto.UserProfileDTO;
import com.moae.enums.IntegrationType;
import com.moae.repository.UserIntegrationRepository;
import com.moae.repository.UserRepository;
import com.moae.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Authentication REST controller — provides session-state queries to the frontend.
 *
 * Only one endpoint lives here:
 *   GET /api/auth/me → returns the current user's profile + integration status.
 *
 * Why no POST /api/auth/logout here:
 *   Spring Security intercepts POST /api/auth/logout at the filter chain level
 *   (configured in SecurityConfig) BEFORE the request reaches any controller.
 *   Adding a logout method here would be dead code — it is never invoked.
 *
 * No business logic lives here — this controller only:
 *   1. Reads the session.
 *   2. Calls repositories.
 *   3. Assembles and returns a DTO.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final UserIntegrationRepository userIntegrationRepository;

    // -------------------------------------------------------------------------
    // GET /api/auth/me
    // -------------------------------------------------------------------------

    /**
     * Returns the profile of the currently authenticated user.
     *
     * <p>Response codes:
     * <ul>
     *   <li>200 OK — UserProfileDTO JSON</li>
     *   <li>401 UNAUTHORIZED — no valid session (thrown by SessionUtil)</li>
     *   <li>404 NOT FOUND — session valid but user deleted from DB</li>
     * </ul>
     *
     * @param session injected by Spring MVC from the current request
     * @return ResponseEntity wrapping UserProfileDTO or an error body
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpSession session) {

        // 1. Resolve userId — throws 401 automatically if session has no userId
        UUID userId = SessionUtil.getUserId(session);

        // 2. Load user from DB — session exists but user may have been deleted
        var userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404)
                .body(Map.of("error", "User not found"));
        }
        var user = userOpt.get();

        // 3. Check live Jira integration status
        boolean jiraConnected = userIntegrationRepository
            .findByUserIdAndIntegrationTypeAndIsActiveTrue(userId, IntegrationType.JIRA)
            .isPresent();

        // 4. Check live Slack integration status
        boolean slackConnected = userIntegrationRepository
            .findByUserIdAndIntegrationTypeAndIsActiveTrue(userId, IntegrationType.SLACK)
            .isPresent();

        // 5. Build outbound DTO
        UserProfileDTO profile = UserProfileDTO.builder()
            .id(user.getId().toString())
            .githubLogin(user.getGithubLogin())
            .name(user.getName())
            .email(user.getEmail())
            .avatarUrl(user.getAvatarUrl())
            .jiraConnected(jiraConnected)
            .slackConnected(slackConnected)
            .build();

        // 6. Return 200 OK
        return ResponseEntity.ok(profile);
    }
}
