package com.moae;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * MOAE — Autonomous Multi-Agent Orchestration Engine
 *
 * Entry point for the Spring Boot application.
 *
 * @SpringBootApplication is a convenience annotation that combines:
 *   @Configuration       — marks this class as a source of bean definitions
 *   @EnableAutoConfiguration — tells Spring Boot to start adding beans based on classpath
 *   @ComponentScan       — scans com.moae and all sub-packages for components
 *
 * @EnableAsync — activates Spring's @Async method execution infrastructure.
 *   Without this, @Async on WorkflowOrchestrator.executeWorkflow() is silently ignored
 *   and the method runs synchronously on the request thread, blocking the HTTP
 *   response for the full pipeline duration (~30s).
 *   With this, Spring wraps @Async calls in a thread pool, allowing the controller
 *   to return 202 Accepted immediately while the pipeline runs in the background.
 *
 * On startup, Hibernate reads all @Entity classes in com.moae.entity and
 * auto-creates / updates tables in moae_db (ddl-auto=update).
 */
@SpringBootApplication
@EnableAsync
public class MoaeApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoaeApplication.class, args);
    }
}
