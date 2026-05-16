package com.moae.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GitHubWebhookPayload {

    private String action;
    
    @JsonProperty("pull_request")
    private PullRequestPayload pull_request;
}
