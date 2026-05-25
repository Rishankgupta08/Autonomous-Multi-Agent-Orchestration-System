package com.moae.util;

/**
 * Utility for extracting clean JSON from raw LLM output.
 *
 * WHY THIS EXISTS:
 *   LLMs often wrap JSON in markdown fences (```json ... ```) or prefix it with
 *   explanation text ("Here is the plan:"). Both PlannerAgent and VerifierAgent
 *   must extract clean JSON before feeding it to ObjectMapper.readValue().
 *   Parsing raw LLM output directly with Jackson throws JsonProcessingException.
 *
 *   Additionally, reasoning-capable models (QwQ, DeepSeek-R1, Qwen3, etc.)
 *   emit a <think>...</think> chain-of-thought block before the real output.
 *   Both methods strip these blocks before searching for JSON delimiters.
 *
 *   Truncation recovery: if the LLM runs out of token budget mid-array,
 *   extractJsonArray() attempts surgical repair by closing unclosed braces
 *   and brackets rather than crashing.
 *
 * Design:
 *   - Static utility class — no Spring annotation, no state.
 *   - Private constructor prevents instantiation.
 *   - Both methods find the outermost delimiter pair (first open, last close)
 *     so they handle nested structures correctly.
 *   - Throws RuntimeException with the FULL raw response in the message so
 *     callers can log exactly what the LLM returned for debugging.
 */
public final class JsonExtractUtil {

    private JsonExtractUtil() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }

    /**
     * Strips {@code <think>...</think>} reasoning blocks from an LLM response.
     * Uses a greedy regex so multiple or nested blocks are all removed.
     * Safe to call when no think blocks are present — returns the input unchanged.
     */
    private static String stripThinkBlocks(String raw) {
        if (raw == null) return "";
        // (?s) enables DOTALL so '.' matches newlines inside the think block
        return raw.replaceAll("(?s)<think>.*?</think>", "").trim();
    }

    /**
     * Extracts the outermost JSON array from a raw LLM response string.
     *
     * Algorithm:
     *  1. Strip think blocks.
     *  2. Find first '['.  If missing → throw (no JSON at all).
     *  3. Find last  ']'.  If missing or before '[' → response was truncated;
     *     attempt {@link #repairTruncatedJsonArray} surgical repair before parsing.
     *  4. Return the clean substring.
     *
     * @param rawLlmResponse the full string returned by the LLM client
     * @return clean (possibly repaired) JSON array string ready for ObjectMapper
     * @throws RuntimeException if no '[' is found at all
     */
    public static String extractJsonArray(String rawLlmResponse) {
        String cleaned = stripThinkBlocks(rawLlmResponse);

        int firstIndex = cleaned.indexOf('[');
        if (firstIndex == -1) {
            throw new RuntimeException(
                "No JSON array found in LLM response: " + rawLlmResponse);
        }

        int lastIndex = cleaned.lastIndexOf(']');
        if (lastIndex == -1 || lastIndex < firstIndex) {
            // Response truncated — attempt surgical repair
            String partial = cleaned.substring(firstIndex);
            return repairTruncatedJsonArray(partial);
        }

        return cleaned.substring(firstIndex, lastIndex + 1);
    }

    /**
     * Attempts to close a truncated JSON array by counting unclosed braces and
     * brackets and appending the required closing characters.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Walk every character tracking brace/bracket depth (ignoring content
     *       inside string literals and respecting backslash escapes).</li>
     *   <li>Strip any trailing comma left by the last incomplete element.</li>
     *   <li>Append {@code '}'} for every unclosed object and {@code ']'} for
     *       every unclosed array.</li>
     * </ol>
     *
     * <p>The result may not be semantically perfect (the last truncated step
     * will have empty or missing fields), but it will be syntactically valid
     * JSON that Jackson can parse — which is all the caller needs.
     */
    private static String repairTruncatedJsonArray(String partial) {
        int braceDepth   = 0;
        int bracketDepth = 0;
        boolean inString = false;
        boolean escape   = false;

        for (char c : partial.toCharArray()) {
            if (escape)        { escape = false; continue; }
            if (c == '\\')     { escape = true;  continue; }
            if (c == '"')      { inString = !inString; continue; }
            if (inString)      { continue; }
            if (c == '{')        braceDepth++;
            else if (c == '}')   braceDepth--;
            else if (c == '[')   bracketDepth++;
            else if (c == ']')   bracketDepth--;
        }

        StringBuilder sb = new StringBuilder(partial.stripTrailing());
        // Remove trailing comma before any closing brackets we add
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ',') {
            sb.deleteCharAt(sb.length() - 1);
        }
        // Close all open objects first, then arrays
        for (int i = 0; i < braceDepth;   i++) sb.append('}');
        for (int i = 0; i < bracketDepth; i++) sb.append(']');

        return sb.toString();
    }

    /**
     * Extracts the outermost JSON object from a raw LLM response string.
     *
     * Algorithm: strip think blocks → find first '{' and last '}' →
     * return the substring between them (inclusive).
     * Handles LLM preamble text and markdown fences.
     *
     * @param raw the full string returned by the LLM client
     * @return clean JSON object string ready for ObjectMapper.readValue(),
     *         or "{}" if no object is found (never throws)
     */
    public static String extractJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        String cleaned = stripThinkBlocks(raw);

        int start = cleaned.indexOf('{');
        int end   = cleaned.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start) {
            return "{}";
        }
        return cleaned.substring(start, end + 1);
    }
}
