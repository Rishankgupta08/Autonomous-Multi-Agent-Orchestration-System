package com.moae.ide;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class IdeSessionRegistry {
    
    // ConcurrentHashMap — thread safe for @Async access
    private final ConcurrentHashMap<String, IdeSession> sessions = new ConcurrentHashMap<>();
    
    public void register(String workflowId, IdeSession session) {
        sessions.put(workflowId, session);
        log.info("IdeSessionRegistry | registered session for workflowId={}", workflowId);
    }
    
    public Optional<IdeSession> get(String workflowId) {
        return Optional.ofNullable(sessions.get(workflowId));
    }
    
    public IdeSession getOrThrow(String workflowId) {
        IdeSession session = sessions.get(workflowId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No active IDE session for workflow: " + workflowId);
        }
        return session;
    }
    
    public void remove(String workflowId) {
        sessions.remove(workflowId);
        log.info("IdeSessionRegistry | removed session for workflowId={}", workflowId);
    }
    
    // Cleanup stale sessions older than 2 hours
    @Scheduled(fixedDelay = 30 * 60 * 1000) // every 30 min
    public void cleanupStaleSessions() {
        long cutoff = System.currentTimeMillis() - (2 * 60 * 60 * 1000);
        sessions.entrySet().removeIf(entry -> {
            boolean stale = entry.getValue().getCreatedAt() < cutoff;
            if (stale) log.info("IdeSessionRegistry | removed stale session: {}", 
                                entry.getKey());
            return stale;
        });
    }
}
