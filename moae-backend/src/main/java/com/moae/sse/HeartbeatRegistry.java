package com.moae.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
@Slf4j
public class HeartbeatRegistry {
    private final ConcurrentHashMap<String, ScheduledFuture<?>> heartbeats = 
        new ConcurrentHashMap<>();
    
    public void register(String workflowId, ScheduledFuture<?> future) {
        heartbeats.put(workflowId, future);
    }
    
    public void cancel(String workflowId) {
        ScheduledFuture<?> future = heartbeats.remove(workflowId);
        if (future != null && !future.isDone()) {
            future.cancel(false);
            log.info("HeartbeatRegistry | cancelled heartbeat for {}", workflowId);
        }
    }
}
