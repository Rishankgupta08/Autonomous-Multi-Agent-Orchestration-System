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
     * Extracts the outermost JSON array from a raw LLM response string.
     *
     * Algorithm: find first '[' and last ']', return the substring between them
     * (inclusive). Handles LLM preamble text and markdown fences.
     *
     * @param rawLlmResponse the full string returned by OllamaClient.generate()
     * @return clean JSON array string ready for ObjectMapper.readValue()
     * @throws RuntimeException if no '[' / ']' pair is found in the response
     */
    public static String extractJsonArray(String rawLlmResponse) {
        int firstIndex = rawLlmResponse.indexOf('[');
        int lastIndex  = rawLlmResponse.lastIndexOf(']');

        if (firstIndex == -1 || lastIndex == -1 || lastIndex < firstIndex) {
            throw new RuntimeException(
                "No JSON array found in LLM response: " + rawLlmResponse);
        }
        return rawLlmResponse.substring(firstIndex, lastIndex + 1);
    }

    /**
     * Extracts the outermost JSON object from a raw LLM response string.
     *
     * Algorithm: find first '{' and last '}', return the substring between them
     * (inclusive). Handles LLM preamble text and markdown fences.
     *
     * @param rawLlmResponse the full string returned by OllamaClient.generate()
     * @return clean JSON object string ready for ObjectMapper.readValue()
     * @throws RuntimeException if no '{' / '}' pair is found in the response
     */
    public static String extractJsonObject(String rawLlmResponse) {
        int firstIndex = rawLlmResponse.indexOf('{');
        int lastIndex  = rawLlmResponse.lastIndexOf('}');

        if (firstIndex == -1 || lastIndex == -1 || lastIndex < firstIndex) {
            throw new RuntimeException(
                "No JSON object found in LLM response: " + rawLlmResponse);
        }
        return rawLlmResponse.substring(firstIndex, lastIndex + 1);
    }
}
