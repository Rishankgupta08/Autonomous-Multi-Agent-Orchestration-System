package com.moae.ide;

import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Getter
public class IdeSession {
    
    private final String workflowId;
    private final String owner;
    private final String repo;
    private final String branch;
    
    // Files the user has opened during review
    // key = filePath, value = ORIGINAL content from GitHub (never changes)
    private final Map<String, String> originalFiles = new LinkedHashMap<>();
    
    // Current state of each file (updated after each AI modification)
    // key = filePath, value = latest content
    private final Map<String, String> currentFiles = new LinkedHashMap<>();
    
    // The primary file being worked on (from generateCode step)
    private final String primaryFilePath;
    
    // Full conversation history for context
    // Each entry: { "role": "user"/"assistant", "content": "..." }
    private final List<Map<String, String>> conversationHistory = new ArrayList<>();
    
    // The original task/instruction from the Jira ticket or goal
    private final String originalTask;
    
    // Timestamp — for cleanup of stale sessions
    private final long createdAt = System.currentTimeMillis();
    
    public IdeSession(String workflowId, String owner, String repo, 
                      String branch, String primaryFilePath, 
                      String primaryFileContent, String originalTask) {
        this.workflowId = workflowId;
        this.owner = owner;
        this.repo = repo;
        this.branch = branch;
        this.primaryFilePath = primaryFilePath;
        this.originalTask = originalTask;
        
        // Register primary file immediately
        this.originalFiles.put(primaryFilePath, primaryFileContent);
        this.currentFiles.put(primaryFilePath, primaryFileContent);
    }
    
    // Register a new file the user opened from the tree
    public void registerFile(String filePath, String content) {
        originalFiles.putIfAbsent(filePath, content); // original never changes
        currentFiles.put(filePath, content);
    }
    
    // Update a file after AI modification
    public void updateFile(String filePath, String newContent) {
        currentFiles.put(filePath, newContent);
    }
    
    // Update a file after user manually edited in Monaco
    public void manualEdit(String filePath, String content) {
        currentFiles.put(filePath, content);
    }
    
    // Add a message to conversation history
    public void addMessage(String role, String content) {
        conversationHistory.add(Map.of("role", role, "content", content));
    }
    
    // Get files that are actually different from original
    public Map<String, String> getModifiedFiles() {
        Map<String, String> modified = new LinkedHashMap<>();
        currentFiles.forEach((path, content) -> {
            String original = originalFiles.get(path);
            if (original == null || !original.equals(content)) {
                modified.put(path, content);
            }
        });
        return modified;
    }
    
    // Get current content of a specific file
    public Optional<String> getFileContent(String filePath) {
        return Optional.ofNullable(currentFiles.get(filePath));
    }
}
