package com.craftguessr.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Extracts the first complete JSON object from a model answer.
 *
 * <p>Even with {@code response_format=json_object} some relays wrap the payload in markdown fences or
 * prepend a sentence; we therefore scan for the first balanced {@code {...}} block and parse that.
 */
public final class LenientJson {

    private LenientJson() {
    }

    public static JsonObject parseObject(String text) throws AiException {
        String extracted = extractObject(text);
        if (extracted == null) {
            throw new AiException("model answer contains no JSON object: " + AiHttp.truncate(text, 300));
        }
        try {
            JsonElement parsed = JsonParser.parseString(extracted);
            if (parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
        } catch (RuntimeException e) {
            throw new AiException("model answer is not valid JSON: " + AiHttp.truncate(extracted, 300), e);
        }
        throw new AiException("model answer JSON is not an object: " + AiHttp.truncate(extracted, 300));
    }

    /** Returns the first balanced object literal, or {@code null} when there is none. */
    public static String extractObject(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        while (start >= 0) {
            String candidate = balancedFrom(text, start);
            if (candidate != null) {
                return candidate;
            }
            start = text.indexOf('{', start + 1);
        }
        return null;
    }

    private static String balancedFrom(String text, int start) {
        Deque<Character> stack = new ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{' -> stack.push('{');
                case '[' -> stack.push('[');
                case '}' -> {
                    if (stack.isEmpty() || stack.pop() != '{') {
                        return null;
                    }
                    if (stack.isEmpty()) {
                        return text.substring(start, i + 1);
                    }
                }
                case ']' -> {
                    if (stack.isEmpty() || stack.pop() != '[') {
                        return null;
                    }
                }
                default -> {
                }
            }
        }
        return null;
    }
}
