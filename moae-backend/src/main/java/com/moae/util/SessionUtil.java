package com.moae.util;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Utility class for reading the current user's UUID from the HttpSession.
 *
 * Design rules:
 *   - Private constructor prevents accidental instantiation.
 *   - All methods are public static — no Spring bean, no injection required.
 *   - Every controller in Steps 3, 4, 7 calls getUserId() in one line
 *     instead of repeating the same null-check boilerplate.
 *
 * Why ResponseStatusException:
 *   Spring MVC automatically converts ResponseStatusException to the correct
 *   HTTP response. Throwing 401 here means the controller body never executes
 *   if the session is invalid — clean separation of concerns.
 */
public final class SessionUtil {

    private SessionUtil() {
        // utility class — no instantiation
    }

    /**
     * Reads the "userId" attribute from the session and parses it as a UUID.
     *
     * @param session the current HttpSession (injected by Spring MVC)
     * @return the authenticated user's UUID
     * @throws ResponseStatusException 401 UNAUTHORIZED if the session has no userId
     *         (session expired, never set, or user not logged in)
     */
    public static UUID getUserId(HttpSession session) {
        String userIdStr = (String) session.getAttribute("userId");
        if (userIdStr == null) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Session expired or not authenticated"
            );
        }
        return UUID.fromString(userIdStr);
    }
}
