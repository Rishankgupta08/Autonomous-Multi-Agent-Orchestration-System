package com.moae.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PullRequestPayload {
    
    @JsonProperty("number")
    private int number;
    
    @JsonProperty("merged")
    private boolean merged;
    
    @JsonProperty("html_url")
    private String html_url;
    
    @JsonProperty("title")
    private String title;
}
