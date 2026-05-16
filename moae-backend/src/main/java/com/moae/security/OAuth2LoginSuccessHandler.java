package com.moae.security;

import com.moae.entity.User;
import com.moae.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Handles post-OAuth2 login success for GitHub.
 *
 * Responsibilities:
 *   1. Extract GitHub user attributes from the OAuth2User principal.
 *   2. Retrieve the GitHub access token via OAuth2AuthorizedClientService
 *      (the token is NOT in the OAuth2User attributes map — Spring Security
 *       keeps it separately in the OAuth2AuthorizedClient).
 *   3. Upsert (update or create) the User record in PostgreSQL.
 *   4. Write the user's UUID to the HttpSession.
 *   5. Redirect browser to the React frontend /integrations page.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;

    @Value("${moae.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    // -------------------------------------------------------------------------
    // Main callback — called by Spring Security after successful OAuth2 login
    // -------------------------------------------------------------------------

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        // Step A — Cast and extract the principal
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = oauthToken.getPrincipal();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // Step B — Extract GitHub user attributes
        String githubLogin = (String) attributes.get("login");
        String name        = (String) attributes.get("name");
        String email       = (String) attributes.get("email");       // null if private email on GitHub
        String avatarUrl   = (String) attributes.get("avatar_url");

        // Step C — Extract access token via OAuth2AuthorizedClientService
        //   The access token is NOT in the attributes map; it lives in the
        //   OAuth2AuthorizedClient that Spring Security manages internally.
        //   This is the only correct approach in Spring Boot 3.x.
        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
            oauthToken.getAuthorizedClientRegistrationId(),
            oauthToken.getName()
        );
        String accessToken = authorizedClient.getAccessToken().getTokenValue();

        // Step D — Upsert user in database
        //   If user already exists → update mutable fields (token rotates on re-auth).
        //   If new user → build and persist a fresh User entity.
        Optional<User> existingUser = userRepository.findByGithubLogin(githubLogin);

        User user;
        if (existingUser.isPresent()) {
            user = existingUser.get();
            user.setGithubAccessToken(accessToken);
            user.setName(name);
            user.setEmail(email);
            user.setAvatarUrl(avatarUrl);
        } else {
            user = User.builder()
                .githubLogin(githubLogin)
                .name(name)
                .email(email)
                .avatarUrl(avatarUrl)
                .githubAccessToken(accessToken)
                .build();
        }

        User savedUser = userRepository.save(user);
        log.info("OAuth2 login success: githubLogin={}, userId={}", githubLogin, savedUser.getId());

        // Step E — Write userId to session as String
        //   UUID stored as String for simplicity; SessionUtil.getUserId() parses it back.
        HttpSession session = request.getSession(true);
        session.setAttribute("userId", savedUser.getId().toString());

        // Step F — Redirect to React frontend
        response.sendRedirect(frontendUrl + "/integrations");
    }
}
