package com.moae.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping("/github")
    public ResponseEntity<String> handleGitHubWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signatureHeader,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestBody byte[] rawBody) {
            
        log.info("GitHub webhook received — event: {}", eventType);
        
        boolean verified = webhookService.verifySignature(rawBody, signatureHeader);
        if (!verified) {
            log.warn("GitHub webhook signature verification FAILED");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }
        
        if ("pull_request".equals(eventType)) {
            webhookService.handlePullRequestEvent(rawBody);
            return ResponseEntity.ok("PR event processed");
        } else {
            log.info("Ignoring non-PR webhook event: {}", eventType);
            return ResponseEntity.ok("Event ignored");
        }
    }
}
