package com.moae.client;

import com.moae.client.dto.GitHubFileResponse;
import com.moae.enums.FailureReason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HTTP adapter for the GitHub REST API v3.
 *
 * Responsibilities:
 *   - Wraps GitHub API endpoints with typed Java method signatures.
 *   - Translates HTTP errors into MoaeClientException with correct FailureReason.
 *   - Handles the GitHub Base64 encoding/decoding quirk (\n every 60 chars).
 *   - Zero session access, zero DB access, zero agent logic.
 *
 * Credentials:
 *   All methods receive a GitHub access token as a parameter.
 *   The token is obtained by ExecutorAgent from the User entity (stored in Step 2).
 *   This client never reads from session or DB directly.
 *
 * Error handling:
 *   HttpClientErrorException (4xx) → CLIENT_ERROR (invalid token, repo not found, etc.)
 *   HttpServerErrorException (5xx) → SERVER_ERROR (GitHub outage)
 *   ResourceAccessException        → TIMEOUT (network unreachable or read timeout hit)
 */
@Component
@Slf4j
public class GitHubClient {

    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final String ACCEPT_HEADER   = "application/vnd.github.v3+json";

    private final RestTemplate restTemplate;

    public GitHubClient(@Qualifier("externalApiRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private HttpHeaders buildGitHubHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Accept", ACCEPT_HEADER);
        return headers;
    }

    private void handleClientError(HttpClientErrorException e, String operation) {
        log.error("GitHub {} failed — HTTP {}: {}", operation, e.getStatusCode().value(),
            e.getResponseBodyAsString());
        throw new MoaeClientException(
            "GitHub " + operation + " failed: HTTP " + e.getStatusCode().value(),
            FailureReason.CLIENT_ERROR,
            e.getStatusCode().value(),
            e
        );
    }

    private void handleServerError(HttpServerErrorException e, String operation) {
        log.error("GitHub {} server error — HTTP {}", operation, e.getStatusCode().value());
        throw new MoaeClientException(
            "GitHub server error during " + operation + ": HTTP " + e.getStatusCode().value(),
            FailureReason.SERVER_ERROR,
            e.getStatusCode().value(),
            e
        );
    }

    private void handleTimeout(ResourceAccessException e, String operation) {
        log.error("GitHub {} timed out: {}", operation, e.getMessage());
        throw new MoaeClientException(
            "GitHub API timeout during " + operation,
            FailureReason.TIMEOUT,
            0,
            e
        );
    }

    // =========================================================================
    // PRIVATE: getDefaultBranchSha — used internally by createBranch
    // =========================================================================

    /**
     * Fetches the tip commit SHA of the given branch.
     * Used by createBranch to obtain the base SHA before creating a new ref.
     *
     * @param owner       repository owner (user or org)
     * @param repo        repository name
     * @param branch      source branch name (e.g. "main")
     * @param accessToken GitHub OAuth token
     * @return tip commit SHA of the branch
     */
    private String getDefaultBranchSha(String owner, String repo,
                                        String branch, String accessToken) {
        String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo
            + "/git/ref/heads/" + branch;
        HttpEntity<Void> entity = new HttpEntity<>(buildGitHubHeaders(accessToken));

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class);
            Map<?, ?> body = response.getBody();
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) body.get("object");
            return (String) object.get("sha");
        } catch (HttpClientErrorException e) {
            handleClientError(e, "getDefaultBranchSha");
        } catch (HttpServerErrorException e) {
            handleServerError(e, "getDefaultBranchSha");
        } catch (ResourceAccessException e) {
            handleTimeout(e, "getDefaultBranchSha");
        }
        throw new IllegalStateException("Unreachable — error handlers always throw");
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Downloads a file from a GitHub repository and returns its decoded content + SHA.
     *
     * IMPORTANT — Base64 newline quirk:
     *   GitHub's API wraps Base64-encoded content at 60 characters using \n.
     *   Standard Java Base64.getDecoder().decode() rejects strings containing \n,
     *   throwing IllegalArgumentException. We strip all \n and \r before decoding.
     *
     * @param owner       repository owner
     * @param repo        repository name
     * @param filePath    path within repo (e.g. "src/main/Calculator.java")
     * @param accessToken GitHub OAuth token
     * @return GitHubFileResponse with decoded content and current file SHA
     */
    public GitHubFileResponse getFile(String owner, String repo,
                                       String filePath, String accessToken) {
        String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo
            + "/contents/" + filePath;
        HttpEntity<Void> entity = new HttpEntity<>(buildGitHubHeaders(accessToken));

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class);
            Map<?, ?> body = response.getBody();

            String rawContent = (String) body.get("content");
            String sha        = (String) body.get("sha");

            // Strip GitHub's 60-char line wrapping before Base64 decode
            String cleanContent = rawContent.replace("\n", "").replace("\r", "");
            String decodedContent = new String(
                Base64.getDecoder().decode(cleanContent), StandardCharsets.UTF_8);

            log.info("GitHub getFile: {}/{}/{} (sha={})", owner, repo, filePath, sha);
            return new GitHubFileResponse(decodedContent, sha);

        } catch (HttpClientErrorException e) {
            handleClientError(e, "getFile");
        } catch (HttpServerErrorException e) {
            handleServerError(e, "getFile");
        } catch (ResourceAccessException e) {
            handleTimeout(e, "getFile");
        }
        throw new IllegalStateException("Unreachable — error handlers always throw");
    }

    /**
     * Creates a new branch from the tip of an existing branch.
     *
     * Internally calls getDefaultBranchSha to resolve the base SHA,
     * then POSTs to /git/refs to create the new ref.
     *
     * @param owner         repository owner
     * @param repo          repository name
     * @param newBranchName name for the new branch
     * @param baseBranch    source branch to branch off (e.g. "main")
     * @param accessToken   GitHub OAuth token
     */
    public void createBranch(String owner, String repo, String newBranchName,
                              String baseBranch, String accessToken) {
        String baseSha = getDefaultBranchSha(owner, repo, baseBranch, accessToken);
        String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/git/refs";

        Map<String, String> body = Map.of(
            "ref", "refs/heads/" + newBranchName,
            "sha", baseSha
        );

        try {
            HttpEntity<Map<String, String>> entity =
                new HttpEntity<>(body, buildGitHubHeaders(accessToken));
            restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            log.info("GitHub createBranch: created '{}' from '{}' (sha: {})",
                newBranchName, baseBranch, baseSha);

        } catch (HttpClientErrorException e) {
            handleClientError(e, "createBranch");
        } catch (HttpServerErrorException e) {
            handleServerError(e, "createBranch");
        } catch (ResourceAccessException e) {
            handleTimeout(e, "createBranch");
        }
    }

    /**
     * Creates or updates a file in a GitHub repository via a commit.
     *
     * IMPORTANT — SHA requirement:
     *   When updating an EXISTING file, fileSha must be the SHA returned by getFile().
     *   Without it, GitHub returns 422 Unprocessable Entity.
     *   When creating a NEW file, fileSha should be null or empty — it is omitted.
     *
     * IMPORTANT — Base64 encoding:
     *   GitHub requires file content to be Base64-encoded in the PUT request body.
     *   Always encode with StandardCharsets.UTF_8 to handle non-ASCII characters.
     *
     * @param owner         repository owner
     * @param repo          repository name
     * @param filePath      path within repo (e.g. "src/fix.java")
     * @param content       raw (plain text) file content to write
     * @param commitMessage git commit message
     * @param branchName    branch to commit to
     * @param fileSha       current file SHA (from getFile); null for new files
     * @param accessToken   GitHub OAuth token
     */
    public void pushFile(String owner, String repo, String filePath,
                          String content, String commitMessage,
                          String branchName, String fileSha, String accessToken) {
        String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo
            + "/contents/" + filePath;

        String encodedContent = Base64.getEncoder()
            .encodeToString(content.getBytes(StandardCharsets.UTF_8));

        // Use HashMap (not Map.of) because Map.of rejects null values
        Map<String, String> body = new HashMap<>();
        body.put("message", commitMessage);
        body.put("content", encodedContent);
        body.put("branch",  branchName);
        if (fileSha != null && !fileSha.isBlank()) {
            body.put("sha", fileSha); // required for updates; omitted for new files
        }

        try {
            HttpEntity<Map<String, String>> entity =
                new HttpEntity<>(body, buildGitHubHeaders(accessToken));
            restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
            log.info("GitHub pushFile: committed '{}' to branch '{}' in {}/{}",
                filePath, branchName, owner, repo);

        } catch (HttpClientErrorException e) {
            handleClientError(e, "pushFile");
        } catch (HttpServerErrorException e) {
            handleServerError(e, "pushFile");
        } catch (ResourceAccessException e) {
            handleTimeout(e, "pushFile");
        }
    }

    /**
     * Opens a Pull Request and returns its URL.
     *
     * @param owner       repository owner
     * @param repo        repository name
     * @param title       PR title
     * @param head        source branch (feature branch)
     * @param base        target branch (e.g. "main")
     * @param accessToken GitHub OAuth token
     * @return full PR URL, e.g. "https://github.com/owner/repo/pull/42"
     */
    public com.moae.client.dto.GitHubPRResponse createPR(String owner, String repo, String title,
                            String head, String base, String accessToken) {
        String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/pulls";

        Map<String, String> body = Map.of(
            "title", title,
            "head",  head,
            "base",  base,
            "body",  "Automated PR created by MOAE"
        );

        try {
            HttpEntity<Map<String, String>> entity =
                new HttpEntity<>(body, buildGitHubHeaders(accessToken));
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, Map.class);
            
            String prUrl = (String) response.getBody().get("html_url");
            Integer prNumber = (Integer) response.getBody().get("number");
            
            log.info("GitHub createPR: {} → {} (#{})", title, prUrl, prNumber);
            return new com.moae.client.dto.GitHubPRResponse(prUrl, prNumber);

        } catch (HttpClientErrorException e) {
            handleClientError(e, "createPR");
        } catch (HttpServerErrorException e) {
            handleServerError(e, "createPR");
        } catch (ResourceAccessException e) {
            handleTimeout(e, "createPR");
        }
        throw new IllegalStateException("Unreachable — error handlers always throw");
    }

    /**
     * Triggers a GitHub Actions workflow dispatch event.
     *
     * IMPORTANT — 204 No Content:
     *   GitHub returns 204 (no body) on success. RestTemplate does NOT throw on 204.
     *   Simply calling exchange and ignoring the response is correct behaviour.
     *
     * @param owner       repository owner
     * @param repo        repository name
     * @param workflowId  workflow filename (e.g. "ci.yml") or numeric ID
     * @param ref         branch or tag to run the workflow on (e.g. "main")
     * @param accessToken GitHub OAuth token
     */
    public void triggerAction(String owner, String repo,
                               String workflowId, String ref, String accessToken) {
        String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo
            + "/actions/workflows/" + workflowId + "/dispatches";

        Map<String, String> body = Map.of("ref", ref);

        try {
            HttpEntity<Map<String, String>> entity =
                new HttpEntity<>(body, buildGitHubHeaders(accessToken));
            // 204 No Content on success — no exception thrown, no response body
            restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            log.info("GitHub triggerAction: triggered '{}' on ref '{}' in {}/{}",
                workflowId, ref, owner, repo);

        } catch (HttpClientErrorException e) {
            handleClientError(e, "triggerAction");
        } catch (HttpServerErrorException e) {
            handleServerError(e, "triggerAction");
        } catch (ResourceAccessException e) {
            handleTimeout(e, "triggerAction");
        }
    }

    // =========================================================================
    // PUBLIC API — Repository tree
    // =========================================================================

    /**
     * Fetches the recursive file tree of a repository branch using the Git Trees API.
     *
     * Files under common noise directories (node_modules, target, dist, etc.) are
     * excluded. Result is capped at 150 entries so the SSE payload stays small.
     *
     * Non-critical: callers should catch Exception and continue on failure.
     *
     * @param owner       repository owner
     * @param repo        repository name
     * @param branch      branch whose tree to fetch (e.g. "main")
     * @param accessToken GitHub OAuth token
     * @return list of maps with keys: path, size, language
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getFileTree(String owner, String repo,
                                                  String branch, String accessToken) {
        String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo
                + "/git/trees/" + branch + "?recursive=1";
        HttpEntity<Void> entity = new HttpEntity<>(buildGitHubHeaders(accessToken));

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class);

        List<Map<String, Object>> tree =
                (List<Map<String, Object>>) response.getBody().get("tree");
        if (tree == null) return new ArrayList<>();

        List<String> EXCLUDE = List.of(
                "node_modules/", ".git/", "dist/", "build/",
                "target/", ".lock", ".class", ".jar"
        );

        List<Map<String, Object>> filtered = tree.stream()
                .filter(item -> "blob".equals(item.get("type")))
                .filter(item -> {
                    String path = (String) item.get("path");
                    return path != null && EXCLUDE.stream().noneMatch(path::contains);
                })
                .limit(150)
                .map(item -> Map.<String, Object>of(
                        "path",     item.get("path"),
                        "size",     item.getOrDefault("size", 0),
                        "language", detectLanguageFromPath((String) item.get("path"))
                ))
                .collect(Collectors.toList());

        log.info("GitHub getFileTree: {}/{} branch='{}' → {} files",
                owner, repo, branch, filtered.size());
        return filtered;
    }

    /**
     * Maps a file path extension to a syntax-highlighter language string.
     * Mirrors the logic in ExecutorAgent / WorkflowService — kept here because
     * GitHubClient is the one embedding language in the tree response.
     */
    private String detectLanguageFromPath(String path) {
        if (path == null) return "plaintext";
        if (path.endsWith(".py"))   return "python";
        if (path.endsWith(".js"))   return "javascript";
        if (path.endsWith(".ts"))   return "typescript";
        if (path.endsWith(".jsx"))  return "javascript";
        if (path.endsWith(".tsx"))  return "typescript";
        if (path.endsWith(".java")) return "java";
        if (path.endsWith(".html") || path.endsWith(".htm")) return "html";
        if (path.endsWith(".css"))  return "css";
        if (path.endsWith(".md"))   return "markdown";
        if (path.endsWith(".json")) return "json";
        if (path.endsWith(".xml"))  return "xml";
        if (path.endsWith(".yml") || path.endsWith(".yaml")) return "yaml";
        if (path.endsWith(".sh"))   return "shell";
        return "plaintext";
    }
}

