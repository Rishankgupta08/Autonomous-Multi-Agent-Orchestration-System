package com.moae.controller;

import com.moae.dto.UserDefaultsDTO;
import com.moae.service.UserDefaultsService;
import com.moae.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST endpoints for managing per-user planner defaults.
 *
 * Controller contract (non-negotiable, matches all other controllers):
 *   - ZERO business logic
 *   - ZERO database calls
 *   - ZERO external HTTP calls
 *   - Every method calls SessionUtil.getUserId(session) FIRST
 *     SessionUtil throws 401 automatically if session is invalid or expired,
 *     so no explicit null-check on session is needed — the throw propagates
 *     to Spring MVC which converts it to a 401 HTTP response.
 *   - Every method delegates immediately to UserDefaultsService
 *
 * Endpoints:
 *   GET  /api/defaults  → load saved defaults (or empty DTO if none saved yet)
 *   POST /api/defaults  → save / update defaults (null-safe partial update)
 *
 * Security:
 *   All /api/** routes are protected by SecurityConfig.
 *   SessionUtil.getUserId() provides the secondary check:
 *   if the session cookie is present but the userId attribute is missing
 *   (session was invalidated or never authenticated) a 401 is thrown.
 */
@RestController
@RequestMapping("/api/defaults")
@RequiredArgsConstructor
@Slf4j
public class UserDefaultsController {

    private final UserDefaultsService userDefaultsService;

    // =========================================================================
    // GET /api/defaults
    // =========================================================================

    /**
     * Returns the current user's planner defaults.
     *
     * If the user has never saved defaults, an all-null DTO is returned (HTTP 200).
     * The frontend interprets null fields as "not configured" and renders empty
     * form fields — it does NOT receive a 404.
     *
     * @param session current HttpSession (JSESSIONID cookie required)
     * @return 200 with UserDefaultsDTO (fields may be null if not yet set)
     *         401 if session is missing or expired (thrown by SessionUtil)
     */
    @GetMapping
    public ResponseEntity<UserDefaultsDTO> getDefaults(HttpSession session) {
        UUID userId = SessionUtil.getUserId(session);   // throws 401 if invalid
        log.debug("GET /api/defaults | userId={}", userId);

        UserDefaultsDTO defaults = userDefaultsService.getDefaults(userId);
        return ResponseEntity.ok(defaults);
    }

    // =========================================================================
    // POST /api/defaults
    // =========================================================================

    /**
     * Saves (creates or updates) the current user's planner defaults.
     *
     * Partial update semantics: null fields in the request body are ignored —
     * they do NOT overwrite existing stored values.  The frontend can send only
     * the field(s) that changed without losing the rest.
     *
     * @param dto     inbound defaults payload; all fields optional (nullable)
     * @param session current HttpSession (JSESSIONID cookie required)
     * @return 200 with the updated UserDefaultsDTO (all fields, including unchanged ones)
     *         401 if session is missing or expired (thrown by SessionUtil)
     */
    @PostMapping
    public ResponseEntity<UserDefaultsDTO> saveDefaults(
            @RequestBody UserDefaultsDTO dto,
            HttpSession session) {

        UUID userId = SessionUtil.getUserId(session);   // throws 401 if invalid
        log.debug("POST /api/defaults | userId={} | body={}", userId, dto);

        UserDefaultsDTO updated = userDefaultsService.saveDefaults(userId, dto);
        return ResponseEntity.ok(updated);
    }
}
