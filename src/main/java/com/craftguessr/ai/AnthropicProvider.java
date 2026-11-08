package com.craftguessr.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Anthropic Messages API adapter ({@code POST /v1/messages}).
 *
 * <p>Works with api.anthropic.com and any relay that speaks the native Messages format, including
 * DeepSeek's {@code anthropic_messages} capability. Images use the {@code base64} source block.
 */
public final class AnthropicProvider implements AiProvider {

    public static final String API_VERSION = "2023-06-01";

    @Override
    public String id() {
        return "anthropic";
    }

    public static JsonObject buildBody(AiRequest request) {
        JsonObject body = new JsonObject();
        body.addProperty("model", request.model());
        body.addProperty("max_tokens", request.maxOutputTokens());
        if (request.temperature() >= 0) {
            body.addProperty("temperature", request.temperature());
        }
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            body.addProperty("system", request.systemPrompt());
        }

        JsonArray content = new JsonArray();
        for (AiRequest.ImagePart image : request.images()) {
            JsonObject source = new JsonObject();
            source.addProperty("type", "base64");
            source.addProperty("media_type", image.mimeType());
            source.addProperty("data", image.base64());
            JsonObject block = new JsonObject();
            block.addProperty("type", "image");
            block.add("source", source);
            content.add(block);
        }
        JsonObject textBlock = new JsonObject();
        textBlock.addProperty("type", "text");
        textBlock.addProperty("text", request.userPrompt());
        content.add(textBlock);

        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.add("content", content);
        JsonArray messages = new JsonArray();
        messages.add(message);
        body.add("messages", messages);
        return body;
    }

    @Override
    public AiResponse complete(AiConnection connection, AiRequest request) throws AiException {
        JsonObject body = buildBody(request);
        JsonObject response = AiHttp.postJson(
                connection.baseUrl() + "/v1/messages",
                body,
                connection.timeout(),
                "x-api-key", connection.apiKey(),
                "anthropic-version", API_VERSION);

        StringBuilder text = new StringBuilder();
        var content = response.get("content");
        if (content != null && content.isJsonArray()) {
            for (var block : content.getAsJsonArray()) {
                if (!block.isJsonObject()) {
                    continue;
                }
                JsonObject obj = block.getAsJsonObject();
                var type = obj.get("type");
                var value = obj.get("text");
                if (type != null && type.isJsonPrimitive() && "text".equals(type.getAsString())
                        && value != null && value.isJsonPrimitive()) {
                    text.append(value.getAsString());
                }
            }
        }
        if (text.isEmpty()) {
            throw new AiException("no text block in Anthropic response: " + AiHttp.truncate(response.toString(), 400), 200, null);
        }
        JsonObject usage = AiHttp.at(response, "usage");
        int prompt = AiHttp.intAt(usage, "input_tokens", 0);
        int completion = AiHttp.intAt(usage, "output_tokens", 0);
        return new AiResponse(text.toString(), prompt, completion, 200, response.toString());
    }
}
