package com.moae.client.dto;

/**
 * Data carrier for the GitHub Get Repository Contents API response.
 *
 * WHY A RECORD (not a class):
 *   This is a pure, immutable data carrier with no behavior.
 *   Java 21 records auto-generate:
 *     - Canonical constructor:   GitHubFileResponse(String content, String sha)
 *     - Component accessors:     content(), sha()
 *     - equals(), hashCode(), toString()
 *   No Lombok annotations needed.
 *
 * Field contract:
 *   content → the decoded (plain text) file content.
 *             GitHubClient fetches the raw Base64-encoded content from the API
 *             and decodes it before constructing this record.
 *             GitHub wraps Base64 at 60 chars with \n — those are stripped before decode.
 *
 *   sha     → the current blob SHA of the file in the repository.
 *             REQUIRED by the GitHub PUT /repos/{owner}/{repo}/contents/{path} endpoint
 *             when UPDATING an existing file. Without this SHA, GitHub returns 422
 *             (Unprocessable Entity) on update attempts.
 *             For new files (first push), sha is not needed and GitHubClient passes null.
 */
public record GitHubFileResponse(String content, String sha) {}
