package com.moae.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for the user_defaults feature — used for both inbound (POST body)
 * and outbound (GET / POST response) payloads.
 *
 * Design rules:
 *   - @Getter + @Setter: this DTO travels both directions (in as @RequestBody,
 *     out as ResponseEntity body), so setters are required for Jackson deserialization
 *     and for the service to build the response from entity fields.
 *   - @Builder: lets UserDefaultsService construct instances fluently.
 *   - All fields are nullable — a user may configure only some defaults.
 *     The service skips null fields on save (no overwriting with null).
 *   - @JsonProperty is declared on every field to make the JSON contract
 *     explicit and immune to Lombok / Jackson version differences.
 *   - No JPA annotations — this is a plain transfer object, not an entity.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDefaultsDTO {

    /**
     * GitHub organisation or personal account login used as the default repo owner.
     * Example: "acme-corp"
     */
    @JsonProperty("githubOwner")
    private String githubOwner;

    /**
     * Default GitHub repository name within the owner's account.
     * Example: "backend-api"
     */
    @JsonProperty("githubDefaultRepo")
    private String githubDefaultRepo;

    /**
     * Jira project key prepended to ticket IDs.
     * Example: "EC"  (so tickets become EC-1, EC-2, …)
     */
    @JsonProperty("jiraProjectKey")
    private String jiraProjectKey;

    /**
     * Default Slack channel for workflow notifications.
     * Example: "#devops"
     */
    @JsonProperty("slackDefaultChannel")
    private String slackDefaultChannel;
}
