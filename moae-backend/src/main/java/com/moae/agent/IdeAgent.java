package com.moae.agent;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.moae.ide.IdeSession;
import com.moae.ide.IdeSessionRegistry;
import com.moae.service.GroqLlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class IdeAgent {
    
    private final GroqLlmService groqLlmService;
    private final IdeSessionRegistry sessionRegistry;
    
    public IdeAgent(GroqLlmService groqLlmService, IdeSessionRegistry sessionRegistry) {
        this.groqLlmService = groqLlmService;
        this.sessionRegistry = sessionRegistry;
    }
    
    /**
     * Core method — modify a target file using AI with full context.
     * Uses deepseek-r1-distill-llama-70b via ideAgentCall().
     */
    public IdeModifyResult modifyFile(
        String workflowId,
        String targetFilePath,    // which file to modify
        String userInstruction    // what the user wants
    ) {
        IdeSession session = sessionRegistry.getOrThrow(workflowId);
        
        // Get current content of target file
        String targetContent = session.getFileContent(targetFilePath)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "File not open in IDE session: " + targetFilePath));
        
        // Build context block with ALL open files
        String contextBlock = buildContextBlock(session, targetFilePath, targetContent);
        
        // Build conversation history summary (last 6 messages to stay within context)
        String historyBlock = buildHistoryBlock(session.getConversationHistory());
        
        // Build the full prompt
        String prompt = buildIdePrompt(
            session.getOriginalTask(),
            targetFilePath,
            contextBlock,
            historyBlock,
            userInstruction
        );
        
        log.info("IdeAgent | modifyFile | workflowId={} | target={} | instruction='{}' | " +
                 "openFiles={} | historySize={}", 
                 workflowId, targetFilePath, userInstruction,
                 session.getCurrentFiles().size(), 
                 session.getConversationHistory().size());
        
        // Call the IDE model
        String rawResponse = groqLlmService.ideAgentCall(prompt);
        
        // Strip any thinking tags (deepseek-r1 sometimes emits <think>...</think>)
        String updatedCode = stripThinkingTags(rawResponse);
        
        // Validate — not a placeholder, not empty
        if (updatedCode == null || updatedCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "IdeAgent: empty response from model");
        }
        
        if (isIterationPlaceholder(updatedCode)) {
            log.warn("IdeAgent | placeholder detected | length={}", updatedCode.length());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "IdeAgent: model returned placeholder — try rephrasing the instruction");
        }
        
        // Update session state
        session.updateFile(targetFilePath, updatedCode);
        session.addMessage("user", userInstruction);
        session.addMessage("assistant", 
            "Updated " + targetFilePath + " (" + updatedCode.length() + " chars)");
        
        int charDiff = updatedCode.length() - targetContent.length();
        log.info("IdeAgent | modifyFile complete | charDiff={} | newLength={}", 
                 charDiff, updatedCode.length());
        
        return new IdeModifyResult(updatedCode, charDiff, targetFilePath);
    }
    
    /**
     * Build the context block with all open files.
     * Target file shown in full. Other files truncated to 800 chars.
     */
    private String buildContextBlock(IdeSession session, 
                                      String targetFilePath, 
                                      String targetContent) {
        StringBuilder sb = new StringBuilder();
        
        // Primary target file — FULL content
        sb.append("━━━ PRIMARY FILE (MODIFY THIS) ━━━\n");
        sb.append("File: ").append(targetFilePath).append("\n");
        sb.append("Content:\n").append(targetContent).append("\n\n");
        
        // Other open files — context only (truncated)
        Map<String, String> otherFiles = session.getCurrentFiles();
        otherFiles.forEach((path, content) -> {
            if (!path.equals(targetFilePath)) {
                String preview = content.length() > 800
                    ? content.substring(0, 800) + "\n... [truncated — " + 
                      content.length() + " total chars]"
                    : content;
                sb.append("━━━ CONTEXT FILE (reference only, do NOT modify) ━━━\n");
                sb.append("File: ").append(path).append("\n");
                sb.append("Content:\n").append(preview).append("\n\n");
            }
        });
        
        return sb.toString();
    }
    
    /**
     * Build conversation history — last 6 messages only to stay within token limits.
     */
    private String buildHistoryBlock(List<Map<String, String>> history) {
        if (history == null || history.isEmpty()) return "No previous messages.";
        
        List<Map<String, String>> recent = history.size() > 6
            ? history.subList(history.size() - 6, history.size())
            : history;
        
        StringBuilder sb = new StringBuilder();
        recent.forEach(msg -> {
            String role = msg.get("role").equals("user") ? "Developer" : "IDE Agent";
            sb.append(role).append(": ").append(msg.get("content")).append("\n");
        });
        return sb.toString();
    }
    
    /**
     * The core prompt sent to deepseek-r1-distill-llama-70b.
     */
    private String buildIdePrompt(String originalTask, String targetFilePath,
                                   String contextBlock, String historyBlock,
                                   String userInstruction) {
        return String.format("""
            You are an expert software engineer working as an Agentic IDE assistant.
            You are helping a developer work on this task: %s
            
            You have access to the developer's open files for context.
            
            %s
            
            CONVERSATION HISTORY:
            %s
            
            CURRENT INSTRUCTION FROM DEVELOPER:
            "%s"
            
            YOUR JOB:
            Modify ONLY the PRIMARY FILE based on the current instruction.
            Use the context files for reference (imports, types, function signatures, styles).
            
            STRICT OUTPUT RULES:
            1. Return ONLY the complete updated content of %s
            2. Zero markdown formatting — no ```code``` blocks
            3. Zero placeholder text of any kind
            4. Zero explanations before or after the code
            5. The file must be complete and immediately runnable
            6. Preserve ALL existing functionality unless explicitly told to remove it
            7. If the instruction is unclear, make the most sensible interpretation
            8. Consider the conversation history — build on previous modifications
            """,
            originalTask,
            contextBlock,
            historyBlock,
            userInstruction,
            targetFilePath
        );
    }
    
    /**
     * Strip <think>...</think> tags from deepseek-r1 responses.
     * deepseek-r1 emits chain-of-thought wrapped in these tags before the actual output.
     */
    private String stripThinkingTags(String response) {
        if (response == null) return null;
        // Remove <think>...</think> block if present
        String stripped = response.replaceAll("(?s)<think>.*?</think>", "").trim();
        return stripped.isBlank() ? response : stripped; // fallback to original if stripping empties it
    }
    
    /**
     * Placeholder check — only rejects genuinely empty/placeholder responses.
     * Does NOT reject real code that contains comments with common words.
     */
    private boolean isIterationPlaceholder(String code) {
        if (code.length() > 300) return false; // real files are never this short
        String lower = code.toLowerCase().trim();
        return lower.equals("generated_code_placeholder")
            || lower.equals("<generated code from previous step>")
            || lower.startsWith("your code here")
            || lower.startsWith("// todo: implement")
            || lower.startsWith("# todo: implement");
    }
    
    // Inner result class
    public record IdeModifyResult(String updatedCode, int charDiff, String filePath) {}
}
