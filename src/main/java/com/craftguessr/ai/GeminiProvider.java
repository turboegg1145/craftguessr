package com.craftguessr.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Google Gemini adapter ({@code POST /v1beta/models/{model}:generateContent}).
 *
 * <p>The API key travels as the {@code key} query parameter, images as {@code inline_data} parts.
 */
public final class GeminiProvider implements AiProvider {

    @Override
    public String id() {
        return "gemini";
    }

    public static JsonObject buildBody(AiRequest request) {
        JsonObject body = new JsonObject();

        JsonArray parts = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", request.userPrompt());
        parts.add(textPart);
        for (AiRequest.ImagePart image : request.images()) {
            JsonObject inline = new JsonObject();
            inline.addProperty("mime_type", image.mimeType());
            inline.addProperty("data", image.base64());
            JsonObject part = new JsonObject();
            part.add("inline_data", inline);
            parts.add(part);
        }
        JsonObject content = new JsonObject();
        content.add("parts", parts);
        JsonArray contents = new JsonArray();
        contents.add(content);
        body.add("contents", contents);

        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            JsonObject sysText = new JsonObject();
            sysText.addProperty("text", request.systemPrompt());
            JsonArray sysParts = new JsonArray();
            sysParts.add(sysText);
            JsonObject systemInstruction = new JsonObject();
            systemInstruction.add("parts", sysParts);
            body.add("systemInstruction", systemInstruction);
        }

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("maxOutputTokens", request.maxOutputTokens());
        if (request.temperature() >= 0) {
            generationConfig.addProperty("temperature", request.temperature());
        }
        if (request.jsonOnly()) {
            generationConfig.addProperty("responseMimeType", "application/json");
        }
        body.add("generationConfig", generationConfig);
        return body;
    }

    @Override
    public AiResponse complete(AiConnection connection, AiRequest request) throws AiException {
        JsonObject body = buildBody(request);
        String url = connection.baseUrl() + "/v1beta/models/" + request.model() + ":generateContent?key=" + connection.apiKey();
        JsonObject response = AiHttp.postJson(url, body, connection.timeout());

        StringBuilder text = new StringBuilder();
        var candidates = response.get("candidates");
        if (candidates != null && candidates.isJsonArray() && !candidates.getAsJsonArray().isEmpty()) {
            var first = candidates.getAsJsonArray().get(0);
            if (first.isJsonObject()) {
                JsonObject content = first.getAsJsonObject().getAsJsonObject("content");
                if (content != null) {
                    var parts = content.get("parts");
                    if (parts != null && parts.isJsonArray()) {
                        for (var part : parts.getAsJsonArray()) {
                            if (part.isJsonObject()) {
                                var t = part.getAsJsonObject().get("text");
                                if (t != null && t.isJsonPrimitive()) {
                                    text.append(t.getAsString());
                                }
                            }
                        }
                    }
                }
            }
        }
        if (text.isEmpty()) {
            throw new AiException("no text candidates in Gemini response: " + AiHttp.truncate(response.toString(), 400), 200, null);
        }
        JsonObject usage = AiHttp.at(response, "usageMetadata");
        int prompt = AiHttp.intAt(usage, "promptTokenCount", 0);
        int completion = AiHttp.intAt(usage, "candidatesTokenCount", 0);
        return new AiResponse(text.toString(), prompt, completion, 200, response.toString());
    }
}
