package com.moae.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry that maps workflowId → SseEmitter.
 *
 * WHY ConcurrentHashMap:
 *   WorkflowController writes to this map on the HTTP request thread.
 *   WorkflowOrchestrator reads from and removes from this map on the @Async thread pool.
 *   ConcurrentHashMap makes these concurrent accesses safe without explicit locking.
 *
 * Lifecycle of a SseEmitter (managed externally — not created here):
 *   1. Created in WorkflowController.streamWorkflow() with a 5-minute timeout.
 *   2. Registered here via register(workflowId, emitter).
 *   3. Events sent via send(workflowId, eventName, data) by WorkflowOrchestrator.
 *   4. Completed via complete(workflowId) after "workflow_complete" is sent.
 *   5. On timeout / client disconnect: onTimeout/onError callbacks call remove().
 *
 * Design rules:
 *   - send() never throws — catches IOException silently (client disconnected = normal).
 *   - complete() never throws — client may already be gone by the time it's called.
 *   - All methods are safe to call with a workflowId that has no registered emitter.
 */
@Component
@Slf4j
public class SseEmitterRegistry {

    /**
     * Active emitters keyed by workflow UUID.
     * ConcurrentHashMap — read/write from multiple threads (request + async pool).
     */
    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * Registers a new SseEmitter for the given workflow.
     * Called by WorkflowController immediately after creating the emitter.
     *
     * @param workflowId UUID of the workflow run
     * @param emitter    the SseEmitter connected to the frontend EventSource
     */
    public void register(UUID workflowId, SseEmitter emitter) {
        emitters.put(workflowId, emitter);
        log.debug("SSE emitter registered for workflowId={}", workflowId);
    }

    /**
     * Sends a named SSE event with JSON data to the registered emitter.
     * If the client has disconnected (IOException), the emitter is silently removed.
     * If no emitter exists for the workflowId, logs a WARN and returns immediately.
     *
     * @param workflowId UUID of the workflow run
     * @param eventName  SSE event name (e.g. "log", "plan_ready", "workflow_complete")
     * @param data       event payload — serialized as application/json
     */
    public void send(UUID workflowId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(workflowId);
        if (emitter == null) {
            log.warn("No emitter found for workflowId={} — event '{}' dropped", workflowId, eventName);
            return;
        }
        try {
            emitter.send(
                SseEmitter.event()
                    .name(eventName)
                    .data(data, MediaType.APPLICATION_JSON)
            );
        } catch (IOException e) {
            // Client disconnected mid-stream — this is expected behaviour, not an error.
            log.warn("SSE send failed for workflowId={} event='{}' — removing dead emitter: {}",
                workflowId, eventName, e.getMessage());
            emitters.remove(workflowId);
        }
    }

    /**
     * Signals the end of the SSE stream by calling emitter.complete().
     * Removes the emitter from the registry.
     * Safe to call if the emitter has already been removed (no-op in that case).
     *
     * @param workflowId UUID of the workflow run
     */
    public void complete(UUID workflowId) {
        SseEmitter emitter = emitters.remove(workflowId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                // Client already gone — ignore silently
                log.debug("SSE complete() ignored for workflowId={} — client already closed: {}",
                    workflowId, e.getMessage());
            }
        }
    }

    /**
     * Removes the emitter from the registry WITHOUT calling complete().\n     * Used by timeout and error callbacks — the emitter is already in a terminal state.
     *
     * @param workflowId UUID of the workflow run
     */
    public void remove(UUID workflowId) {
        emitters.remove(workflowId);
        log.debug("SSE emitter removed for workflowId={}", workflowId);
    }

    // ── String-accepting convenience overloads ─────────────────────────────────
    // ExecutorAgent and WorkflowOrchestrator carry workflowId as String;
    // these overloads avoid UUID.fromString() boilerplate at every call site.

    /** @see #send(UUID, String, Object) */
    public void send(String workflowId, String eventName, Object data) {
        send(UUID.fromString(workflowId), eventName, data);
    }

    /** @see #complete(UUID) */
    public void complete(String workflowId) {
        complete(UUID.fromString(workflowId));
    }
}
