package com.moae.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configures two RestTemplate beans with different timeout profiles.
 *
 * WHY TWO BEANS:
 *   External APIs (GitHub, Jira, Slack) respond within 1–5 seconds under normal load.
 *   A 15-second read timeout is generous without blocking threads unnecessarily.
 *
 *   Ollama LLM inference is CPU/GPU-bound and can take 30–90 seconds for complex prompts,
 *   especially on CPU-only hardware. A 15-second timeout would kill every Ollama call.
 *   The dedicated ollamaRestTemplate uses a 120-second read timeout.
 *
 *   Timeout values are externalized to application.properties so they can be tuned
 *   per environment (local dev vs cloud VM with GPU) without recompilation.
 *
 * Usage:
 *   @Qualifier("externalApiRestTemplate") → GitHubClient, JiraClient, SlackClient
 *   @Qualifier("ollamaRestTemplate")      → OllamaClient
 */
@Configuration
public class RestTemplateConfig {

    private final int externalConnectTimeout;
    private final int externalReadTimeout;
    private final int ollamaConnectTimeout;
    private final int ollamaReadTimeout;

    public RestTemplateConfig(
            @Value("${moae.client.external.connect-timeout}") int externalConnectTimeout,
            @Value("${moae.client.external.read-timeout}")    int externalReadTimeout,
            @Value("${moae.client.ollama.connect-timeout}")   int ollamaConnectTimeout,
            @Value("${moae.client.ollama.read-timeout}")      int ollamaReadTimeout) {
        this.externalConnectTimeout = externalConnectTimeout;
        this.externalReadTimeout    = externalReadTimeout;
        this.ollamaConnectTimeout   = ollamaConnectTimeout;
        this.ollamaReadTimeout      = ollamaReadTimeout;
    }

    /**
     * RestTemplate for GitHub, Jira, and Slack API calls.
     * Connect timeout: 10s | Read timeout: 15s
     */
    @Bean
    @Qualifier("externalApiRestTemplate")
    public RestTemplate externalApiRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(externalConnectTimeout);
        factory.setReadTimeout(externalReadTimeout);
        return new RestTemplate(factory);
    }

    /**
     * RestTemplate for Ollama LLM inference.
     * Connect timeout: 10s | Read timeout: 120s (LLM inference is slow on CPU)
     */
    @Bean
    @Qualifier("ollamaRestTemplate")
    public RestTemplate ollamaRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(ollamaConnectTimeout);
        factory.setReadTimeout(ollamaReadTimeout);
        return new RestTemplate(factory);
    }
}
